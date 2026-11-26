import { describe, expect, it } from "vitest";

import { isGuestOnlyPath, isProtectedPath } from "./routes";

describe("routes", () => {
  it("보호 화면: 마이·저장·추천과 그 하위 경로", () => {
    expect(isProtectedPath("/my")).toBe(true);
    expect(isProtectedPath("/my/profile")).toBe(true);
    expect(isProtectedPath("/saved")).toBe(true);
    expect(isProtectedPath("/recommend")).toBe(true);
    expect(isProtectedPath("/")).toBe(false);
    expect(isProtectedPath("/search")).toBe(false);
    expect(isProtectedPath("/mypage")).toBe(false);
  });

  it("비회원 전용 화면: 로그인·가입·비밀번호 찾기 (재설정은 제외)", () => {
    expect(isGuestOnlyPath("/login")).toBe(true);
    expect(isGuestOnlyPath("/signup")).toBe(true);
    expect(isGuestOnlyPath("/forgot-password")).toBe(true);
    expect(isGuestOnlyPath("/reset-password")).toBe(false);
  });
});
