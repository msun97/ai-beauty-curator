import "client-only";

import { ApiError, networkError, readBody, toApiError } from "./errors";

/**
 * 브라우저(클라이언트 컴포넌트)용 API 호출 계층 (ADR-004).
 *
 * - 같은 도메인의 /api/** 로 요청하고 Next.js가 백엔드로 전달한다 (next.config.ts rewrites).
 * - 토큰은 HttpOnly 쿠키라 자바스크립트에서 다루지 않는다. 쿠키는 브라우저가 자동으로 보낸다.
 * - 모든 요청에 CSRF 방지 헤더(X-Requested-With)를 붙인다.
 * - 401이면 토큰을 한 번 재발급하고 다시 요청한다. 재발급도 실패하면 세션 만료로 알린다.
 */

export const CSRF_HEADER = "X-Requested-With";
const CSRF_VALUE = "fetch";
const REFRESH_PATH = "/api/auth/refresh";
const DEFAULT_TIMEOUT_MS = 10_000;

// --- 세션 만료 알림 ---------------------------------------------------------

type SessionExpiredListener = () => void;
const sessionExpiredListeners = new Set<SessionExpiredListener>();

/**
 * 재발급까지 실패해 로그인이 풀렸을 때 호출될 함수를 등록한다 (예: 로그인 화면으로 이동).
 * 반환값을 호출하면 등록이 해제된다.
 */
export function onSessionExpired(listener: SessionExpiredListener): () => void {
  sessionExpiredListeners.add(listener);
  return () => sessionExpiredListeners.delete(listener);
}

function notifySessionExpired() {
  sessionExpiredListeners.forEach((listener) => listener());
}

// --- 토큰 재발급 ------------------------------------------------------------

let refreshing: Promise<boolean> | null = null;

/**
 * 여러 요청이 동시에 401을 받아도 재발급 요청은 한 번만 보낸다.
 * 백엔드는 이미 사용된 Refresh Token이 다시 들어오면 탈취로 보고 로그인을 모두 해제하기 때문이다.
 */
function refreshSession(): Promise<boolean> {
  refreshing ??= fetch(REFRESH_PATH, {
    method: "POST",
    credentials: "same-origin",
    headers: { [CSRF_HEADER]: CSRF_VALUE },
  })
    .then((res) => res.ok)
    .catch(() => false)
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}

// --- 저수준 호출 ------------------------------------------------------------

export type ApiRequestInit = RequestInit & { timeoutMs?: number };

/**
 * 응답을 그대로 돌려주는 저수준 호출. 상태 코드를 직접 다뤄야 할 때만 쓰고,
 * 보통은 아래 clientApi를 쓴다. 네트워크 오류·타임아웃은 ApiError(NETWORK_ERROR)로 던진다.
 */
export async function apiFetch(path: string, init: ApiRequestInit = {}): Promise<Response> {
  const { timeoutMs = DEFAULT_TIMEOUT_MS, ...requestInit } = init;

  const send = async () => {
    const headers = new Headers(requestInit.headers);
    headers.set(CSRF_HEADER, CSRF_VALUE);
    if (requestInit.body && !headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }
    try {
      return await fetch(path, {
        ...requestInit,
        headers,
        credentials: "same-origin",
        signal: requestInit.signal ?? AbortSignal.timeout(timeoutMs),
      });
    } catch {
      throw networkError();
    }
  };

  const res = await send();
  if (res.status !== 401 || path.startsWith("/api/auth/")) {
    return res;
  }
  if (await refreshSession()) {
    return send();
  }
  notifySessionExpired();
  return res;
}

// --- 타입이 있는 호출 -------------------------------------------------------

async function request<T>(method: string, path: string, body?: unknown, init?: ApiRequestInit): Promise<T> {
  const res = await apiFetch(path, {
    ...init,
    method,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!res.ok) {
    throw await toApiError(res);
  }
  return readBody<T>(res);
}

/**
 * 성공하면 응답 본문(JSON)을, 실패하면 ApiError를 던진다.
 *
 * @example
 * try {
 *   const user = await clientApi.post<AuthUser>("/api/auth/login", { email, password });
 * } catch (e) {
 *   if (e instanceof ApiError) setError(e.fieldMessage("email") ?? e.message);
 * }
 */
export const clientApi = {
  get: <T>(path: string, init?: ApiRequestInit) => request<T>("GET", path, undefined, init),
  post: <T>(path: string, body?: unknown, init?: ApiRequestInit) => request<T>("POST", path, body, init),
  put: <T>(path: string, body?: unknown, init?: ApiRequestInit) => request<T>("PUT", path, body, init),
  patch: <T>(path: string, body?: unknown, init?: ApiRequestInit) => request<T>("PATCH", path, body, init),
  delete: <T>(path: string, init?: ApiRequestInit) => request<T>("DELETE", path, undefined, init),
};

export { ApiError };
