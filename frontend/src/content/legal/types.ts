/**
 * 약관·개인정보 문서의 구조 (FIX-005).
 *
 * 문서 본문을 마크다운 대신 데이터로 두어, 바텀시트와 /terms·/privacy 페이지가 같은 원본을 같은 모양으로 그리게 한다.
 * 문장 안의 강조는 `**굵게**`로 표시하고, 화면에서는 parseEmphasis로 나눠 그린다.
 */

export type LegalBlock =
  | { type: "paragraph"; text: string }
  | { type: "list"; ordered: boolean; items: readonly string[] }
  | { type: "table"; caption: string; head: readonly string[]; rows: readonly (readonly string[])[] };

export type LegalSection = {
  /** 조항 제목. 예: "제1조 (목적)" */
  heading: string;
  blocks: readonly LegalBlock[];
};

export type LegalDocumentId = "terms" | "privacyConsent" | "skinConsent" | "privacyPolicy";

export type LegalDocument = {
  id: LegalDocumentId;
  title: string;
  /** 조항 앞에 오는 머리말 */
  intro: readonly LegalBlock[];
  sections: readonly LegalSection[];
  /** 부칙(시행일 문장). 동의서처럼 부칙이 없는 문서는 생략 */
  addendum?: string;
};
