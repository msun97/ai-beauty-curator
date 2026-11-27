import Link from "next/link";

import { formatEffectiveDate, LEGAL_VERSION, type LegalDocument as LegalDocumentData } from "@/content/legal";

import { LegalDocument } from "./LegalDocument";

type LegalPageProps = {
  document: LegalDocumentData;
  /** 함께 볼 다른 문서 (약관 ↔ 처리방침) */
  related: { href: string; label: string };
};

/** /terms·/privacy 화면 본문: 제목, 버전·시행일, 문서 본문, 다른 문서 링크 */
export function LegalPage({ document, related }: LegalPageProps) {
  return (
    <main className="flex flex-col px-screen pt-4">
      <h1 className="text-2xl font-bold text-ink-strong">{document.title}</h1>
      <p className="mt-1 text-xs text-muted">
        버전 {LEGAL_VERSION} · 시행일 {formatEffectiveDate()}
      </p>
      <div className="mt-6">
        <LegalDocument document={document} sectionHeading="h2" />
      </div>
      <Link
        href={related.href}
        className="mt-8 inline-flex min-h-11 items-center gap-1 self-start text-sm text-muted underline underline-offset-4 hover:text-ink"
      >
        {related.label}
      </Link>
    </main>
  );
}
