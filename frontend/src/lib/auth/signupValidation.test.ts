import { describe, expect, it } from "vitest";

import {
  EMPTY_DETAILS,
  normalizeCode,
  toSignupRequest,
  validateCode,
  validateDetails,
  validateEmail,
  type DetailValues,
} from "./signupValidation";

const VALID: DetailValues = {
  password: "abcd1234",
  passwordConfirm: "abcd1234",
  nickname: "하린",
  agreements: { terms: true, privacy: true, skinData: true },
};

describe("validateEmail", () => {
  it("빈 값·흔한 오타를 잡고, 앞뒤 공백은 허용", () => {
    expect(validateEmail("")).toBe("이메일을 입력해 주세요.");
    for (const email of ["harin", "harin@", "harin@gmail", "ha rin@example.com"]) {
      expect(validateEmail(email), email).toBe("이메일 형식이 올바르지 않아요.");
    }
    expect(validateEmail("  harin@example.com  ")).toBeUndefined();
  });
});

describe("인증 코드", () => {
  it("숫자만 남기고 6자리로 자른다 (붙여넣기 대응)", () => {
    expect(normalizeCode("123 456")).toBe("123456");
    expect(normalizeCode("12-34-56-78")).toBe("123456");
    expect(normalizeCode("abc")).toBe("");
  });

  it("6자리가 아니면 안내", () => {
    expect(validateCode("")).toBe("인증 코드를 입력해 주세요.");
    expect(validateCode("12345")).toBe("인증 코드 6자리를 입력해 주세요.");
    expect(validateCode("123456")).toBeUndefined();
  });
});

describe("validateDetails", () => {
  it("모두 올바르면 오류 없음 (닉네임은 비워도 됨)", () => {
    expect(validateDetails(VALID)).toEqual({});
    expect(validateDetails({ ...VALID, nickname: "" })).toEqual({});
  });

  it("빈 폼이면 필수 항목마다 안내", () => {
    expect(validateDetails(EMPTY_DETAILS)).toEqual({
      password: "비밀번호를 입력해 주세요.",
      passwordConfirm: "비밀번호를 한 번 더 입력해 주세요.",
      agreements: "필수 약관에 모두 동의해 주세요.",
    });
  });

  it("비밀번호 규칙과 확인 일치", () => {
    expect(validateDetails({ ...VALID, password: "abcdefgh", passwordConfirm: "abcdefgh" }).password).toContain("영문과 숫자");
    expect(validateDetails({ ...VALID, passwordConfirm: "abcd12345" }).passwordConfirm).toBe("비밀번호가 일치하지 않아요.");
  });

  it("닉네임 20자까지 허용, 21자부터 안내", () => {
    expect(validateDetails({ ...VALID, nickname: "가".repeat(20) }).nickname).toBeUndefined();
    expect(validateDetails({ ...VALID, nickname: "가".repeat(21) }).nickname).toBe("닉네임은 20자 이하로 입력해 주세요.");
  });

  it("필수 약관 하나라도 빠지면 안내 (피부 정보 동의 포함)", () => {
    expect(validateDetails({ ...VALID, agreements: { terms: true, privacy: true, skinData: false } }).agreements).toBe(
      "필수 약관에 모두 동의해 주세요.",
    );
  });
});

describe("toSignupRequest", () => {
  it("인증 토큰을 담고, 비밀번호 확인은 보내지 않으며, 빈 닉네임은 생략", () => {
    expect(toSignupRequest("token-1", { ...VALID, nickname: "  " })).toEqual({
      verificationToken: "token-1",
      password: "abcd1234",
      nickname: undefined,
      agreements: { terms: true, privacy: true, skinData: true },
    });
  });
});
