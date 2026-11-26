import { describe, expect, it } from "vitest";

import { ApiError, networkError } from "@/lib/api/errors";

import { firstErrorField, toFormErrors } from "./formErrors";

const FIELDS = ["email", "password"] as const;

function apiError(code: string, message: string, fieldErrors: { field: string; message: string }[] = []) {
  return new ApiError({ status: 400, code, message, fieldErrors, traceId: "trace-1" });
}

describe("toFormErrors", () => {
  it("항목별 오류는 입력란에 붙이고, 위쪽 박스는 띄우지 않는다", () => {
    const result = toFormErrors(
      apiError("INVALID_INPUT", "입력값을 확인해 주세요.", [
        { field: "email", message: "이메일을 입력해 주세요." },
        { field: "password", message: "비밀번호를 입력해 주세요." },
      ]),
      FIELDS,
    );
    expect(result.fields).toEqual({ email: "이메일을 입력해 주세요.", password: "비밀번호를 입력해 주세요." });
    expect(result.form).toBeNull();
  });

  it("업무 오류도 fieldErrors로 오면 같은 방식으로 입력란에 붙는다 (이메일 중복)", () => {
    const result = toFormErrors(
      apiError("EMAIL_DUPLICATED", "이미 가입된 이메일이에요.", [{ field: "email", message: "이미 가입된 이메일이에요." }]),
      FIELDS,
    );
    expect(result.fields).toEqual({ email: "이미 가입된 이메일이에요." });
    expect(result.form).toBeNull();
  });

  it("항목이 없는 오류는 위쪽 박스로 (로그인 실패, 잠금)", () => {
    const result = toFormErrors(apiError("INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않아요."), FIELDS);
    expect(result.fields).toEqual({});
    expect(result.form).toEqual({
      code: "INVALID_CREDENTIALS",
      message: "이메일 또는 비밀번호가 올바르지 않아요.",
      traceId: "trace-1",
    });
  });

  it("이 폼에 없는 항목의 오류가 섞여 있으면 위쪽 박스도 함께 띄운다", () => {
    const result = toFormErrors(
      apiError("INVALID_INPUT", "입력값을 확인해 주세요.", [
        { field: "email", message: "형식 오류" },
        { field: "unknown", message: "?" },
      ]),
      FIELDS,
    );
    expect(result.fields).toEqual({ email: "형식 오류" });
    expect(result.form?.message).toBe("입력값을 확인해 주세요.");
  });

  it("같은 항목의 오류가 여러 개면 첫 번째만", () => {
    const result = toFormErrors(
      apiError("INVALID_INPUT", "입력값을 확인해 주세요.", [
        { field: "password", message: "첫 번째" },
        { field: "password", message: "두 번째" },
      ]),
      FIELDS,
    );
    expect(result.fields.password).toBe("첫 번째");
  });

  it("네트워크 오류·알 수 없는 오류도 위쪽 박스로", () => {
    expect(toFormErrors(networkError(), FIELDS).form?.code).toBe("NETWORK_ERROR");
    expect(toFormErrors(new Error("boom"), FIELDS).form?.message).toContain("잠시 후 다시 시도");
  });
});

describe("firstErrorField", () => {
  it("화면 순서대로 첫 오류 항목", () => {
    expect(firstErrorField(FIELDS, { password: "x" })).toBe("password");
    expect(firstErrorField(FIELDS, { email: "x", password: "y" })).toBe("email");
    expect(firstErrorField(FIELDS, {})).toBeUndefined();
  });
});
