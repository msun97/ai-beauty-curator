import type { Metadata } from "next";

import { LegalPage } from "@/components/legal/LegalPage";
import { LEGAL_DOCUMENTS } from "@/content/legal";

export const metadata: Metadata = { title: LEGAL_DOCUMENTS.privacyPolicy.title };

export default function PrivacyPage() {
  return (
    <LegalPage document={LEGAL_DOCUMENTS.privacyPolicy} related={{ href: "/terms", label: "서비스 이용약관 보기" }} />
  );
}
