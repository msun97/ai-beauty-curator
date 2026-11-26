import { ApiError, describeError } from "@/lib/api/errors";

/**
 * 폼 오류를 "입력란별 오류"와 "폼 전체 오류"로 나눈다.
 *
 * - 백엔드는 입력값 검증 오류와, 특정 항목 때문에 생긴 업무 오류(예: 이메일 중복)를
 *   모두 fieldErrors에 { field, message }로 내려준다. 화면은 이 한 가지 방식만 처리하면 된다.
 * - 이 폼에 없는 항목의 오류나 항목과 무관한 오류(예: 로그인 실패, 네트워크 오류)는 폼 전체 오류로 보여준다.
 */

export type FieldErrors<F extends string> = Partial<Record<F, string>>;

export type FormErrors<F extends string> = {
  fields: FieldErrors<F>;
  /** 폼 위쪽(FormAlert)에 보여줄 오류. 입력란별 오류만 있으면 null */
  form: { code: string | null; message: string; traceId: string | null } | null;
};

export const NO_ERRORS = { fields: {}, form: null } as const satisfies FormErrors<never>;

export function toFormErrors<F extends string>(error: unknown, fields: readonly F[]): FormErrors<F> {
  if (!(error instanceof ApiError)) {
    const { message, traceId } = describeError(error);
    return { fields: {}, form: { code: null, message, traceId } };
  }

  const known = new Set<string>(fields);
  const fieldErrors: FieldErrors<F> = {};
  for (const { field, message } of error.fieldErrors) {
    // 같은 항목에 오류가 여러 개면 첫 번째만 보여준다
    if (known.has(field) && fieldErrors[field as F] === undefined) {
      fieldErrors[field as F] = message;
    }
  }

  const hasFieldError = Object.keys(fieldErrors).length > 0;
  const unmatched = error.fieldErrors.some((e) => !known.has(e.field));
  // 입력란에 붙인 오류만 있으면 위쪽 박스는 띄우지 않는다 (같은 말을 두 번 보여주지 않기)
  const form =
    hasFieldError && !unmatched ? null : { code: error.code, message: error.message, traceId: error.traceId };
  return { fields: fieldErrors, form };
}

/** 오류가 있는 첫 입력란 이름 (그 칸으로 초점을 옮길 때 쓴다) */
export function firstErrorField<F extends string>(order: readonly F[], fields: FieldErrors<F>): F | undefined {
  return order.find((field) => fields[field] !== undefined);
}
