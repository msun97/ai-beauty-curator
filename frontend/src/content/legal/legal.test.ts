import { existsSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

import { describe, expect, it } from "vitest";

import { SIGNUP_AGREEMENTS } from "@/lib/auth/agreements";

import { AGREEMENT_DOCUMENTS, LEGAL_DOCUMENTS } from "./index";
import { addendumText, formatEffectiveDate, LEGAL_VERSION } from "./meta";
import { parseEmphasis } from "./emphasis";
import type { LegalBlock, LegalDocument } from "./types";

function allTexts(doc: LegalDocument): string[] {
  const fromBlock = (block: LegalBlock): string[] =>
    block.type === "paragraph"
      ? [block.text]
      : block.type === "list"
        ? [...block.items]
        : [block.caption, ...block.head, ...block.rows.flat()];
  return [
    doc.title,
    ...doc.intro.flatMap(fromBlock),
    ...doc.sections.flatMap((s) => [s.heading, ...s.blocks.flatMap(fromBlock)]),
    ...(doc.addendum ? [doc.addendum] : []),
  ];
}

describe("약관 버전·시행일", () => {
  it("약관 버전이 백엔드 app.auth.terms-version과 같다 (동의 기록에 저장되는 버전)", () => {
    const yml = fileURLToPath(new URL("../../../../backend/src/main/resources/application.yml", import.meta.url));
    if (!existsSync(yml)) return; // 프론트만 따로 받은 경우
    const match = /terms-version:\s*"?([\w-]+)"?/.exec(readFileSync(yml, "utf8"));
    expect(match?.[1]).toBe(LEGAL_VERSION);
  });

  it("시행일을 한국어 날짜로 보여주고, 정하지 않았으면 '정식 공개일'", () => {
    expect(formatEffectiveDate("2026-12-01")).toBe("2026년 12월 1일");
    expect(formatEffectiveDate(null)).toBe("정식 공개일");
    expect(() => formatEffectiveDate("2026/12/01")).toThrow();
  });

  it("부칙 문장에 시행일과 버전이 들어간다", () => {
    expect(addendumText("약관", "2026-12-01")).toBe(`이 약관은 2026년 12월 1일부터 시행합니다. (버전 ${LEGAL_VERSION})`);
  });

  it("게시용 문서(약관·처리방침)에는 부칙이 있다", () => {
    expect(LEGAL_DOCUMENTS.terms.addendum).toContain(LEGAL_VERSION);
    expect(LEGAL_DOCUMENTS.privacyPolicy.addendum).toContain(LEGAL_VERSION);
  });
});

describe("문서 내용", () => {
  it("가입 화면의 모든 동의 항목에 '보기'로 띄울 문서가 있다", () => {
    for (const item of SIGNUP_AGREEMENTS) {
      expect(AGREEMENT_DOCUMENTS[item.key], item.key).toBeDefined();
    }
  });

  it("초안의 빈칸 표시(【…】)나 짝이 안 맞는 강조 표시가 남아 있지 않다", () => {
    for (const doc of Object.values(LEGAL_DOCUMENTS)) {
      for (const text of allTexts(doc)) {
        expect(text, doc.id).not.toMatch(/[【】]|결정 필요|직접 기입/);
        expect((text.match(/\*\*/g) ?? []).length % 2, `${doc.id}: ${text}`).toBe(0);
      }
    }
  });

  it("표의 각 행은 머리글과 칸 수가 같다", () => {
    for (const doc of Object.values(LEGAL_DOCUMENTS)) {
      const tables = [...doc.intro, ...doc.sections.flatMap((s) => s.blocks)].filter((b) => b.type === "table");
      for (const table of tables) {
        for (const row of table.rows) expect(row.length, `${doc.id}: ${table.caption}`).toBe(table.head.length);
      }
    }
  });

  it("처리방침에 보호책임자 연락처가 있다", () => {
    expect(allTexts(LEGAL_DOCUMENTS.privacyPolicy).join("\n")).toMatch(/@/);
  });
});

describe("parseEmphasis", () => {
  it("**로 감싼 부분만 강조 조각이 된다", () => {
    expect(parseEmphasis("앞 **강조** 뒤")).toEqual([
      { text: "앞 ", strong: false },
      { text: "강조", strong: true },
      { text: " 뒤", strong: false },
    ]);
    expect(parseEmphasis("**처음부터**")).toEqual([{ text: "처음부터", strong: true }]);
    expect(parseEmphasis("강조 없음")).toEqual([{ text: "강조 없음", strong: false }]);
  });

  it("짝이 안 맞는 **는 글자 그대로 둔다", () => {
    expect(parseEmphasis("가 **나")).toEqual([{ text: "가 **나", strong: false }]);
    expect(parseEmphasis("**가** 다 **라")).toEqual([
      { text: "가", strong: true },
      { text: " 다 **라", strong: false },
    ]);
  });
});
