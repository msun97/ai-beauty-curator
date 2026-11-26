/**
 * 회원가입 약관 동의 항목. key는 백엔드 ReqSignupDTO.Agreements 필드명과 같다.
 * 피부 정보 수집·이용은 민감한 정보라 개인정보 동의와 별도 항목으로 받는다 (FR-AUTH-01).
 */

export type AgreementItem<K extends string = string> = {
  key: K;
  label: string;
  required: boolean;
};

export type AgreementKey = "terms" | "privacy" | "skinData";

export const SIGNUP_AGREEMENTS: ReadonlyArray<AgreementItem<AgreementKey>> = [
  { key: "terms", label: "서비스 이용약관", required: true },
  { key: "privacy", label: "개인정보 수집·이용", required: true },
  { key: "skinData", label: "피부 정보 수집·이용", required: true },
];

export type AgreementState<K extends string = string> = Record<K, boolean>;

export function emptyAgreements<K extends string>(items: ReadonlyArray<AgreementItem<K>>): AgreementState<K> {
  return Object.fromEntries(items.map((item) => [item.key, false])) as AgreementState<K>;
}

/** 전체 동의: 모든 항목을 같은 값으로 */
export function setAllAgreements<K extends string>(
  items: ReadonlyArray<AgreementItem<K>>,
  checked: boolean,
): AgreementState<K> {
  return Object.fromEntries(items.map((item) => [item.key, checked])) as AgreementState<K>;
}

/** 모든 항목에 동의했으면 "전체 동의"도 체크된 것으로 보여준다 */
export function isAllAgreed<K extends string>(items: ReadonlyArray<AgreementItem<K>>, state: AgreementState<K>): boolean {
  return items.length > 0 && items.every((item) => state[item.key]);
}

/** 아직 동의하지 않은 필수 항목 */
export function missingRequired<K extends string>(
  items: ReadonlyArray<AgreementItem<K>>,
  state: AgreementState<K>,
): AgreementItem<K>[] {
  return items.filter((item) => item.required && !state[item.key]);
}
