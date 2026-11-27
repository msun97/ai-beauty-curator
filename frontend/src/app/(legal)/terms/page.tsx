import type { Metadata } from "next";

import { LegalPage } from "@/components/legal/LegalPage";
import { LEGAL_DOCUMENTS } from "@/content/legal";

export const metadata: Metadata = { title: LEGAL_DOCUMENTS.terms.title };

export default function TermsPage() {
  return <LegalPage document={LEGAL_DOCUMENTS.terms} related={{ href: "/privacy", label: "개인정보 처리방침 보기" }} />;
}
