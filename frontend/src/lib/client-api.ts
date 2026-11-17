/**
 * 브라우저(클라이언트 컴포넌트)에서 쓰는 API 호출 함수.
 * - 같은 도메인의 /api/** 로 요청하고, Next.js가 백엔드로 전달한다 (next.config.ts rewrites).
 * - 토큰은 HttpOnly 쿠키라 자바스크립트에서 다루지 않는다.
 * - 상태 변경 요청의 CSRF 방지를 위해 X-Requested-With 헤더를 항상 붙인다.
 * - 401이면 토큰을 한 번 재발급한 뒤 다시 요청한다.
 */

let refreshing: Promise<boolean> | null = null;

/**
 * 여러 요청이 동시에 401을 받아도 재발급 요청은 한 번만 보낸다.
 * 백엔드는 이미 사용된 Refresh Token이 다시 들어오면 탈취로 보고 로그인을 모두 해제하기 때문이다.
 */
function refreshSession(): Promise<boolean> {
  refreshing ??= fetch("/api/auth/refresh", {
    method: "POST",
    credentials: "same-origin",
    headers: { "X-Requested-With": "fetch" },
  })
    .then((res) => res.ok)
    .catch(() => false)
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}

export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const send = () => {
    const headers = new Headers(init.headers);
    headers.set("X-Requested-With", "fetch");
    if (init.body && !headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }
    return fetch(path, { ...init, headers, credentials: "same-origin" });
  };

  const res = await send();
  if (res.status === 401 && !path.startsWith("/api/auth/") && (await refreshSession())) {
    return send();
  }
  return res;
}

/** 백엔드 공통 오류 응답 형식 (NFR-REL-02) */
export type ApiError = {
  code: string;
  message: string;
  fieldErrors: { field: string; message: string }[];
  /** 문의 시 서버 로그를 찾기 위한 요청 추적 ID */
  traceId: string | null;
};

const FALLBACK_ERROR: ApiError = {
  code: "NETWORK_ERROR",
  message: "연결이 원활하지 않아요. 잠시 후 다시 시도해 주세요.",
  fieldErrors: [],
  traceId: null,
};

/** 실패 응답을 공통 오류 형식으로 읽는다. 본문이 형식에 맞지 않으면(프록시 오류 등) 기본 문구를 쓴다. */
export async function readApiError(res: Response): Promise<ApiError> {
  try {
    const body = (await res.json()) as Partial<ApiError>;
    if (typeof body.code === "string" && typeof body.message === "string") {
      return {
        code: body.code,
        message: body.message,
        fieldErrors: body.fieldErrors ?? [],
        traceId: body.traceId ?? res.headers.get("X-Request-Id"),
      };
    }
  } catch {
    // JSON이 아닌 응답
  }
  return { ...FALLBACK_ERROR, traceId: res.headers.get("X-Request-Id") };
}
