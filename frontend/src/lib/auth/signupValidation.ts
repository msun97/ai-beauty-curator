import { emptyAgreements, missingRequired, SIGNUP_AGREEMENTS, type AgreementKey, type AgreementState } from "./agreements";
import { isPasswordTooLong, isValidPassword, PASSWORD_MAX_LENGTH, PASSWORD_RULE_MESSAGE } from "./passwordRule";

/**
 * 회원가입 입력값 화면 검사 (FR-AUTH-01). 최종 판단은 백엔드가 하고, 여기서는 바로 안내할 수 있는 것만 본다.
 * 가입은 ① 이메일 → ② 인증 코드 → ③ 정보 입력 단계로 나뉘므로 검사도 단계별로 둔다.
 */

export const NICKNAME_MAX_LENGTH = 20;
export const VERIFICATION_CODE_LENGTH = 6;
const EMAIL_MAX_LENGTH = 254;

/** "@"와 도메인의 점까지 확인해 흔한 오타(gmail만 입력 등)를 잡는다 */
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function validateEmail(raw: string): string | undefined {
  const email = raw.trim();
  if (!email) return "이메일을 입력해 주세요.";
  if (email.length > EMAIL_MAX_LENGTH) return "이메일이 너무 길어요.";
  if (!EMAIL_PATTERN.test(email)) return "이메일 형식이 올바르지 않아요.";
  return undefined;
}

/** 붙여넣기한 "123 456" 같은 값에서 숫자만 남긴다 */
export function normalizeCode(raw: string): string {
  return raw.replace(/\D/g, "").slice(0, VERIFICATION_CODE_LENGTH);
}

export function validateCode(code: string): string | undefined {
  if (!code) return "인증 코드를 입력해 주세요.";
  if (code.length !== VERIFICATION_CODE_LENGTH) return `인증 코드 ${VERIFICATION_CODE_LENGTH}자리를 입력해 주세요.`;
  return undefined;
}

export const DETAIL_FIELDS = ["password", "passwordConfirm", "nickname", "agreements"] as const;
export type DetailField = (typeof DETAIL_FIELDS)[number];

export type DetailValues = {
  password: string;
  passwordConfirm: string;
  nickname: string;
  agreements: AgreementState<AgreementKey>;
};

export const EMPTY_DETAILS: DetailValues = {
  password: "",
  passwordConfirm: "",
  nickname: "",
  agreements: emptyAgreements(SIGNUP_AGREEMENTS),
};

export function validateDetails(values: DetailValues): Partial<Record<DetailField, string>> {
  const errors: Partial<Record<DetailField, string>> = {};

  if (!values.password) errors.password = "비밀번호를 입력해 주세요.";
  else if (isPasswordTooLong(values.password)) errors.password = `비밀번호는 ${PASSWORD_MAX_LENGTH}자 이하로 입력해 주세요.`;
  else if (!isValidPassword(values.password)) errors.password = PASSWORD_RULE_MESSAGE;

  if (!values.passwordConfirm) errors.passwordConfirm = "비밀번호를 한 번 더 입력해 주세요.";
  else if (values.passwordConfirm !== values.password) errors.passwordConfirm = "비밀번호가 일치하지 않아요.";

  if (values.nickname.trim().length > NICKNAME_MAX_LENGTH) {
    errors.nickname = `닉네임은 ${NICKNAME_MAX_LENGTH}자 이하로 입력해 주세요.`;
  }

  if (missingRequired(SIGNUP_AGREEMENTS, values.agreements).length > 0) {
    errors.agreements = "필수 약관에 모두 동의해 주세요.";
  }
  return errors;
}

/** 백엔드 가입 요청 모양. 비밀번호 확인은 화면에서만 쓰고 보내지 않는다. */
export function toSignupRequest(verificationToken: string, values: DetailValues) {
  const nickname = values.nickname.trim();
  return {
    verificationToken,
    password: values.password,
    // 비워 두면 백엔드가 이메일 앞부분으로 정한다
    nickname: nickname || undefined,
    agreements: { ...values.agreements },
  };
}
