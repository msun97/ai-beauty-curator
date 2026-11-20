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
