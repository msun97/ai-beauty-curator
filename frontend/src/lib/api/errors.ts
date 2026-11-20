/**
 * 백엔드 공통 오류 응답(ResErrorDTO, NFR-REL-02)을 다루는 공용 모듈.
 * 서버·브라우저 양쪽에서 쓴다.
 */

export type FieldError = { field: string; message: string };

/** 응답 헤더와 오류 본문에 담기는 요청 추적 ID (NFR-REL-04) */
export const TRACE_ID_HEADER = "X-Request-Id";

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: FieldError[];
  readonly traceId: string | null;

  constructor(params: {
    status: number;
    code: string;
    message: string;
    fieldErrors?: FieldError[];
    traceId?: string | null;
  }) {
    super(params.message);
    this.name = "ApiError";
    this.status = params.status;
    this.code = params.code;
    this.fieldErrors = params.fieldErrors ?? [];
    this.traceId = params.traceId ?? null;
  }

  /** 입력란 아래에 표시할 필드별 오류 메시지 */
  fieldMessage(field: string): string | undefined {
    return this.fieldErrors.find((e) => e.field === field)?.message;
  }
}

/** 네트워크 단절, 타임아웃 등 응답 자체를 받지 못한 경우 */
export const NETWORK_ERROR_CODE = "NETWORK_ERROR";
const NETWORK_ERROR_MESSAGE = "연결이 원활하지 않아요. 잠시 후 다시 시도해 주세요.";

export function networkError(): ApiError {
  return new ApiError({ status: 0, code: NETWORK_ERROR_CODE, message: NETWORK_ERROR_MESSAGE });
}

/**
 * 실패 응답을 ApiError로 바꾼다.
 * 본문이 공통 형식이 아니면(프록시 오류 페이지, HTML 등) 기본 문구를 쓴다.
 */
export async function toApiError(res: Response): Promise<ApiError> {
  const headerTraceId = res.headers.get(TRACE_ID_HEADER);
  try {
    const body = (await res.json()) as Partial<{
      code: string;
      message: string;
      fieldErrors: FieldError[];
      traceId: string | null;
    }>;
    if (typeof body.code === "string" && typeof body.message === "string") {
      return new ApiError({
        status: res.status,
        code: body.code,
        message: body.message,
        fieldErrors: Array.isArray(body.fieldErrors) ? body.fieldErrors : [],
        traceId: body.traceId ?? headerTraceId,
      });
    }
  } catch {
    // JSON이 아닌 응답
  }
  return new ApiError({
    status: res.status,
    code: res.status >= 500 ? "INTERNAL_ERROR" : "UNKNOWN_ERROR",
    message: NETWORK_ERROR_MESSAGE,
    traceId: headerTraceId,
  });
}

/** 성공 응답 본문을 읽는다. 204 또는 빈 본문이면 undefined. */
export async function readBody<T>(res: Response): Promise<T> {
  if (res.status === 204) {
    return undefined as T;
  }
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

// --- 화면 표시용 -------------------------------------------------------------

export const GENERIC_ERROR_MESSAGE = "일시적인 문제가 생겼어요. 잠시 후 다시 시도해 주세요.";

export type ErrorDisplay = {
  message: string;
  /** 문의할 때 알려줄 식별자. API 오류는 요청 추적 ID, 서버 컴포넌트 오류는 Next.js digest. */
  traceId: string | null;
};

/**
 * 어떤 값이 throw 되었든 사용자에게 보여줄 문구와 식별자로 바꾼다.
 * - ApiError: 백엔드가 준 문구(사용자용으로 작성됨)와 traceId를 그대로 쓴다.
 * - 그 밖의 오류: 내부 메시지가 노출되지 않게 기본 문구를 쓴다.
 *   서버 컴포넌트 오류는 운영 환경에서 digest만 전달되므로 이를 식별자로 쓴다.
 */
export function describeError(error: unknown): ErrorDisplay {
  if (error instanceof ApiError) {
    return { message: error.message, traceId: error.traceId };
  }
  const digest =
    typeof error === "object" && error !== null && "digest" in error && typeof error.digest === "string"
      ? error.digest
      : null;
  return { message: GENERIC_ERROR_MESSAGE, traceId: digest };
}
