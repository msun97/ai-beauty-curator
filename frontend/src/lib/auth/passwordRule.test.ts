import { describe, expect, it } from "vitest";

import { PASSWORD_RULES, checkPasswordRules, isPasswordTooLong, isValidPassword } from "./passwordRule";

describe("passwordRule", () => {
  it("항목별로 충족 여부를 알려준다", () => {
    expect(checkPasswordRules("")).toEqual({ letter: false, digit: false, length: false });
    expect(checkPasswordRules("abc")).toEqual({ letter: true, digit: false, length: false });
    expect(checkPasswordRules("12345678")).toEqual({ letter: false, digit: true, length: true });
    expect(checkPasswordRules("abcd1234")).toEqual({ letter: true, digit: true, length: true });
  });

  it("백엔드 규칙과 같은 경계값: 8자·64자 통과, 7자·65자 실패", () => {
    expect(isValidPassword("abcd123")).toBe(false);
    expect(isValidPassword("abcd1234")).toBe(true);
    expect(isValidPassword("a1" + "x".repeat(62))).toBe(true);
    expect(isValidPassword("a1" + "x".repeat(63))).toBe(false);
    expect(isPasswordTooLong("a1" + "x".repeat(63))).toBe(true);
  });

  it("영문 없음·숫자 없음은 실패, 특수문자·공백은 허용", () => {
    expect(isValidPassword("12345678")).toBe(false);
    expect(isValidPassword("abcdefgh")).toBe(false);
    expect(isValidPassword("ab cd!@12")).toBe(true);
  });

  it("한글은 영문으로 치지 않는다", () => {
    expect(isValidPassword("비밀번호12345")).toBe(false);
  });

  it("체크 목록의 모든 항목을 만족하면 (64자 이하에서) 규칙 전체도 만족한다", () => {
    for (const value of ["abcd1234", "Z9zzzzzz", "pass word 1"]) {
      const allRules = PASSWORD_RULES.every((rule) => rule.test(value));
      expect(allRules).toBe(isValidPassword(value));
    }
  });
});
