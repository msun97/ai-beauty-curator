import { describe, expect, it } from "vitest";

import { loginPath, safeNextPath } from "./nextPath";

describe("safeNextPath", () => {
  it("내부 경로는 쿼리·해시까지 그대로 허용한다", () => {
    expect(safeNextPath("/products/42")).toBe("/products/42");
    expect(safeNextPath("/search?q=%EC%84%B8%EB%9F%BC&tab=product")).toBe("/search?q=%EC%84%B8%EB%9F%BC&tab=product");
    expect(safeNextPath("/my#profile")).toBe("/my#profile");
  });

  it("없거나 비어 있으면 홈", () => {
    expect(safeNextPath(null)).toBe("/");
    expect(safeNextPath(undefined)).toBe("/");
    expect(safeNextPath("")).toBe("/");
  });

  it("외부 사이트로 보내는 값은 홈으로 바꾼다", () => {
    for (const evil of [
      "https://evil.example",
      "//evil.example",
      "/\\evil.example",
      "\\\\evil.example",
      "javascript:alert(1)",
      "evil.example",
      "/\t/evil.example",
      "/\n/evil.example",
    ]) {
      expect(safeNextPath(evil), evil).toBe("/");
    }
  });

  it("로그인·가입 화면으로는 돌려보내지 않는다", () => {
    expect(safeNextPath("/login")).toBe("/");
    expect(safeNextPath("/login?next=/my")).toBe("/");
    expect(safeNextPath("/signup")).toBe("/");
    expect(safeNextPath("/reset-password?token=abc")).toBe("/");
    expect(safeNextPath("/loginx")).toBe("/loginx");
  });
});

describe("loginPath", () => {
  it("돌아올 곳이 있으면 next로 담는다", () => {
    expect(loginPath("/products/42?tab=review")).toBe("/login?next=%2Fproducts%2F42%3Ftab%3Dreview");
  });

  it("돌아올 곳이 홈이거나 안전하지 않으면 next 없이", () => {
    expect(loginPath()).toBe("/login");
    expect(loginPath("/")).toBe("/login");
    expect(loginPath("https://evil.example")).toBe("/login");
  });
});
