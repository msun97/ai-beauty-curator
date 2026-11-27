import type { AgreementKey } from "@/lib/auth/agreements";

import { PRIVACY_CONSENT, SKIN_CONSENT } from "./consents";
import { PRIVACY_POLICY } from "./privacyPolicy";
import { TERMS } from "./terms";
import type { LegalDocument, LegalDocumentId } from "./types";

export type { LegalBlock, LegalDocument, LegalDocumentId, LegalSection } from "./types";
export { parseEmphasis, type TextSegment } from "./emphasis";
export { formatEffectiveDate, LEGAL_EFFECTIVE_DATE, LEGAL_VERSION } from "./meta";

export const LEGAL_DOCUMENTS: Readonly<Record<LegalDocumentId, LegalDocument>> = {
  terms: TERMS,
  privacyConsent: PRIVACY_CONSENT,
  skinConsent: SKIN_CONSENT,
  privacyPolicy: PRIVACY_POLICY,
};

/** 가입 화면 동의 항목의 "보기"에서 띄울 문서 */
export const AGREEMENT_DOCUMENTS: Readonly<Record<AgreementKey, LegalDocument>> = {
  terms: TERMS,
  privacy: PRIVACY_CONSENT,
  skinData: SKIN_CONSENT,
};
