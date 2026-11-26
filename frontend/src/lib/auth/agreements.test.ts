import { describe, expect, it } from "vitest";

import {
  SIGNUP_AGREEMENTS,
  emptyAgreements,
  isAllAgreed,
  missingRequired,
  setAllAgreements,
  type AgreementItem,
} from "./agreements";

describe("agreements", () => {
  it("처음에는 모두 동의하지 않은 상태", () => {
    expect(emptyAgreements(SIGNUP_AGREEMENTS)).toEqual({ terms: false, privacy: false, skinData: false });
  });

  it("전체 동의를 켜고 끄면 모든 항목이 따라 바뀐다", () => {
    expect(setAllAgreements(SIGNUP_AGREEMENTS, true)).toEqual({ terms: true, privacy: true, skinData: true });
    expect(setAllAgreements(SIGNUP_AGREEMENTS, false)).toEqual({ terms: false, privacy: false, skinData: false });
  });

  it("개별 항목을 모두 체크하면 전체 동의도 체크, 하나라도 빠지면 해제", () => {
    expect(isAllAgreed(SIGNUP_AGREEMENTS, { terms: true, privacy: true, skinData: true })).toBe(true);
    expect(isAllAgreed(SIGNUP_AGREEMENTS, { terms: true, privacy: true, skinData: false })).toBe(false);
  });

  it("빠진 필수 항목을 알려준다 (선택 항목은 제외)", () => {
    const items: AgreementItem[] = [
      { key: "a", label: "필수 A", required: true },
      { key: "b", label: "선택 B", required: false },
    ];
    expect(missingRequired(items, { a: false, b: false }).map((i) => i.key)).toEqual(["a"]);
    expect(missingRequired(items, { a: true, b: false })).toEqual([]);
  });

  it("가입 항목 key는 백엔드 Agreements 필드명과 같다", () => {
    expect(SIGNUP_AGREEMENTS.map((i) => i.key)).toEqual(["terms", "privacy", "skinData"]);
  });
});
