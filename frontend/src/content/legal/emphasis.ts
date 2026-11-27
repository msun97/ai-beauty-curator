/** 문장 안의 `**굵게**` 표시를 일반 글자와 강조 글자 조각으로 나눈다 */
export type TextSegment = { text: string; strong: boolean };

export function parseEmphasis(text: string): TextSegment[] {
  const segments: TextSegment[] = [];
  const parts = text.split("**");
  // 짝이 맞지 않는 마지막 ** 는 강조로 보지 않고 글자 그대로 둔다
  const unmatched = parts.length % 2 === 0;
  parts.forEach((part, index) => {
    const isLast = index === parts.length - 1;
    if (unmatched && isLast) {
      const prev = segments.at(-1);
      const literal = `**${part}`;
      if (prev && !prev.strong) prev.text += literal;
      else segments.push({ text: literal, strong: false });
      return;
    }
    if (part) segments.push({ text: part, strong: index % 2 === 1 });
  });
  return segments;
}
