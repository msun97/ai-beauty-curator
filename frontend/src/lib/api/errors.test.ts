import { describe, expect, it } from "vitest";

import { ApiError, GENERIC_ERROR_MESSAGE, describeError } from "./errors";

describe("describeError", () => {
  it("ApiError는 백엔드 문구와 trace ID를 그대로 쓴다", () => {
    const error = new ApiError({ status: 400, code: "INVALID_INPUT", message: "입력값을 확인해 주세요.", traceId: "a1b2c3d4e5f60718" });
    expect(describeError(error)).toEqual({ message: "입력값을 확인해 주세요.", traceId: "a1b2c3d4e5f60718" });
  });

  it("서버 컴포넌트 오류는 기본 문구와 digest를 쓴다", () => {
    const error = Object.assign(new Error("DB password wrong"), { digest: "1234567890" });
    expect(describeError(error)).toEqual({ message: GENERIC_ERROR_MESSAGE, traceId: "1234567890" });
  });

  it("Error가 아닌 값이 throw 되어도 기본 문구를 쓴다", () => {
    expect(describeError("boom")).toEqual({ message: GENERIC_ERROR_MESSAGE, traceId: null });
    expect(describeError(null)).toEqual({ message: GENERIC_ERROR_MESSAGE, traceId: null });
  });
});
