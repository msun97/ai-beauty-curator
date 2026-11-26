/**
 * 비밀번호 규칙: 영문·숫자를 포함한 8~64자 (FR-AUTH-01).
 * 백엔드 PasswordRule(auth/api/dto/PasswordRule.java)과 같은 규칙이다. 한쪽을 바꾸면 다른 쪽도 함께 바꾼다.
 * 화면 검사는 입력을 돕는 용도이고, 최종 판단은 백엔드가 한다.
 */

export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_LENGTH = 64;

/** 백엔드 PasswordRule.REGEX와 같은 식 */
export const PASSWORD_REGEX = /^(?=.*[A-Za-z])(?=.*\d).{8,64}$/;

export const PASSWORD_RULE_MESSAGE = "비밀번호는 영문과 숫자를 포함해 8자 이상이어야 해요.";

export type PasswordRuleId = "letter" | "digit" | "length";

/** 입력하는 동안 체크로 보여줄 항목 */
export const PASSWORD_RULES: ReadonlyArray<{ id: PasswordRuleId; label: string; test: (value: string) => boolean }> = [
  { id: "letter", label: "영문 포함", test: (v) => /[A-Za-z]/.test(v) },
  { id: "digit", label: "숫자 포함", test: (v) => /\d/.test(v) },
  { id: "length", label: `${PASSWORD_MIN_LENGTH}자 이상`, test: (v) => v.length >= PASSWORD_MIN_LENGTH },
];

export function checkPasswordRules(value: string): Record<PasswordRuleId, boolean> {
  return Object.fromEntries(PASSWORD_RULES.map((rule) => [rule.id, rule.test(value)])) as Record<
    PasswordRuleId,
    boolean
  >;
}

export function isPasswordTooLong(value: string): boolean {
  return value.length > PASSWORD_MAX_LENGTH;
}

export function isValidPassword(value: string): boolean {
  return PASSWORD_REGEX.test(value);
}
