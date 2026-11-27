import { parseEmphasis, type LegalBlock, type LegalDocument as LegalDocumentData } from "@/content/legal";

type LegalDocumentProps = {
  document: LegalDocumentData;
  /**
   * 조항 제목의 단계. 문서 제목보다 한 단계 아래여야 한다.
   * - 바텀시트: 시트 제목이 h2이므로 "h3"
   * - /terms·/privacy 페이지: 페이지 제목이 h1이므로 "h2"
   */
  sectionHeading: "h2" | "h3";
};

/**
 * 약관·개인정보 문서 본문 (FIX-005).
 * 문서 제목은 그리지 않는다 — 바텀시트 머리글이나 페이지 제목이 맡는다.
 * 바텀시트와 /terms·/privacy 페이지가 같은 원본(src/content/legal)을 같은 모양으로 그린다.
 */
export function LegalDocument({ document, sectionHeading: Heading }: LegalDocumentProps) {
  return (
    // 한국어가 단어 중간에서 줄바꿈되지 않게(keep-all), 이메일처럼 긴 글자는 넘칠 때만 끊는다
    <div className="flex flex-col gap-6 text-sm break-keep text-ink [overflow-wrap:anywhere]">
      {document.intro.length > 0 && <Blocks blocks={document.intro} />}
      {document.sections.map((section) => (
        <section key={section.heading} className="flex flex-col gap-2">
          <Heading className="text-base font-bold text-ink-strong">{section.heading}</Heading>
          <Blocks blocks={section.blocks} />
        </section>
      ))}
      {document.addendum && (
        <p className="border-t border-line pt-4 text-muted">
          <span className="font-bold text-ink">부칙</span> {document.addendum}
        </p>
      )}
    </div>
  );
}

function Blocks({ blocks }: { blocks: readonly LegalBlock[] }) {
  return (
    <div className="flex flex-col gap-2">
      {blocks.map((block, index) => (
        <Block key={index} block={block} />
      ))}
    </div>
  );
}

function Block({ block }: { block: LegalBlock }) {
  switch (block.type) {
    case "paragraph":
      return (
        <p>
          <Emphasis text={block.text} />
        </p>
      );
    case "list": {
      const List = block.ordered ? "ol" : "ul";
      return (
        <List className={`flex flex-col gap-1 pl-5 ${block.ordered ? "list-decimal" : "list-disc"}`}>
          {block.items.map((item) => (
            <li key={item} className="pl-0.5 marker:text-muted">
              <Emphasis text={item} />
            </li>
          ))}
        </List>
      );
    }
    case "table":
      return <Table caption={block.caption} head={block.head} rows={block.rows} />;
  }
}

/**
 * 표. 칸이 많은 표(국외 이전 6칸)는 휴대폰 폭을 넘으므로 표만 가로로 스크롤한다.
 * 스크롤 영역은 키보드로도 움직일 수 있게 포커스를 받고, 스크린 리더에는 표 제목으로 알린다.
 */
function Table({ caption, head, rows }: Omit<Extract<LegalBlock, { type: "table" }>, "type">) {
  const wide = head.length > 3;
  return (
    <div
      role="region"
      aria-label={caption}
      tabIndex={0}
      className="overflow-x-auto rounded-md border border-line focus-visible:outline-2 focus-visible:outline-brand"
    >
      <table className={`w-full border-collapse text-left text-xs ${wide ? "min-w-[640px]" : ""}`}>
        <caption className="sr-only">{caption}</caption>
        <thead className="bg-surface-muted">
          <tr>
            {head.map((cell) => (
              <th key={cell} scope="col" className="border-b border-line px-3 py-2 font-bold whitespace-nowrap">
                {cell}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, rowIndex) => (
            <tr key={rowIndex} className="border-b border-line last:border-b-0">
              {row.map((cell, cellIndex) =>
                cellIndex === 0 ? (
                  <th key={cellIndex} scope="row" className="px-3 py-2 align-top font-medium">
                    <Emphasis text={cell} />
                  </th>
                ) : (
                  <td key={cellIndex} className="px-3 py-2 align-top">
                    <Emphasis text={cell} />
                  </td>
                ),
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Emphasis({ text }: { text: string }) {
  return parseEmphasis(text).map((segment, index) =>
    segment.strong ? (
      <strong key={index} className="font-bold text-ink-strong">
        {segment.text}
      </strong>
    ) : (
      segment.text
    ),
  );
}
