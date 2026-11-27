/**
 * 약관 버전과 시행일 — 이 파일 한 곳에서만 바꾼다 (FIX-005).
 *
 * - LEGAL_VERSION은 백엔드 app.auth.terms-version과 같아야 한다. 가입 때 동의 기록(user_consents)에 이 버전이 저장된다.
 *   (meta.test.ts가 백엔드 application.yml과 값이 같은지 확인한다)
 * - LEGAL_EFFECTIVE_DATE는 8주차 배포일에 "YYYY-MM-DD"로 채운다. 그 전에는 null이고 화면에는 "정식 공개일"로 보인다.
 * - 약관 내용을 바꾸면 버전을 올리고, 새 시행일을 넣는다 (약관 제3조: 시행 7일 전, 불리한 변경은 30일 전 공지).
 */

export const LEGAL_VERSION = "2026-11";

export const LEGAL_EFFECTIVE_DATE: string | null = null;

export const SERVICE_NAME = "pickly";

/** 운영자이자 개인정보 보호책임자 */
export const OPERATOR_NAME = "김미선";

/** 개인정보 처리방침 7항에 공개하는 문의 이메일 */
export const PRIVACY_CONTACT_EMAIL = "miseong827@gmail.com";

/** "2026-12-01" → "2026년 12월 1일". 아직 정하지 않았으면(null) "정식 공개일" */
export function formatEffectiveDate(date: string | null = LEGAL_EFFECTIVE_DATE): string {
  if (date === null) return "정식 공개일";
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date);
  if (!match) throw new Error(`시행일 형식이 잘못됐습니다 (YYYY-MM-DD): ${date}`);
  const [, year, month, day] = match;
  return `${year}년 ${Number(month)}월 ${Number(day)}일`;
}

/** 부칙 문장. 예: "이 약관은 2026년 12월 1일부터 시행합니다. (버전 2026-11)" */
export function addendumText(subject: string, date: string | null = LEGAL_EFFECTIVE_DATE): string {
  return `이 ${subject}은 ${formatEffectiveDate(date)}부터 시행합니다. (버전 ${LEGAL_VERSION})`;
}
