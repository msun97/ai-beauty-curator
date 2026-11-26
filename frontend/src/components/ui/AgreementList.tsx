"use client";

import { useId } from "react";

import {
  isAllAgreed,
  setAllAgreements,
  type AgreementItem,
  type AgreementState,
} from "@/lib/auth/agreements";

import { Checkbox } from "./Checkbox";
import { Icon } from "./Icon";

type AgreementListProps<K extends string> = {
  items: ReadonlyArray<AgreementItem<K>>;
  value: AgreementState<K>;
  onChange: (next: AgreementState<K>) => void;
  /** "보기"를 눌렀을 때 실행할 동작(약관 바텀시트 열기). 없으면 "보기" 버튼을 표시하지 않는다 */
  onView?: (item: AgreementItem<K>) => void;
  /** 필수 항목을 빠뜨리고 제출했을 때 보여줄 문구 */
  error?: string;
};

/**
 * 약관 동의: "전체 동의"와 개별 항목, 각 항목의 "보기" 버튼(약관 전문은 바텀시트로 띄운다).
 * - 전체 동의를 누르면 모든 항목이 같이 바뀌고, 개별 항목을 모두 체크하면 전체 동의도 체크된다.
 * - 각 체크박스는 name=항목 key, value="true"로 폼에 함께 제출된다.
 */
export function AgreementList<K extends string>({ items, value, onChange, onView, error }: AgreementListProps<K>) {
  const allAgreed = isAllAgreed(items, value);
  const errorId = useId();

  return (
    <fieldset aria-describedby={error ? errorId : undefined}>
      <legend className="sr-only">약관 동의</legend>
      <div className={`rounded-md border px-4 py-1 ${error ? "border-danger" : "border-line"}`}>
        <Checkbox
          checked={allAgreed}
          onChange={(e) => onChange(setAllAgreements(items, e.target.checked))}
          className="border-b border-line"
        >
          <span className="font-bold">전체 동의</span>
        </Checkbox>
        <ul>
          {items.map((item) => (
            <li key={item.key} className="flex items-center justify-between gap-2">
              <Checkbox
                name={item.key}
                value="true"
                checked={value[item.key]}
                onChange={(e) => onChange({ ...value, [item.key]: e.target.checked })}
                className="flex-1"
              >
                {item.label}{" "}
                <span className={item.required ? "text-brand" : "text-muted"}>({item.required ? "필수" : "선택"})</span>
              </Checkbox>
              {onView && (
                <button
                  type="button"
                  onClick={() => onView(item)}
                  aria-haspopup="dialog"
                  className="inline-flex min-h-11 shrink-0 items-center gap-0.5 rounded-md text-xs text-muted underline-offset-2 hover:text-ink hover:underline focus-visible:outline-2 focus-visible:outline-brand"
                >
                  보기
                  <span className="sr-only">: {item.label}</span>
                  <Icon name="chevronRight" size={14} />
                </button>
              )}
            </li>
          ))}
        </ul>
      </div>
      {error && (
        <p id={errorId} className="mt-1.5 text-xs text-danger">
          {error}
        </p>
      )}
    </fieldset>
  );
}
