import type { InputHTMLAttributes, ReactNode } from "react";

import { Icon } from "./Icon";

type CheckboxProps = Omit<InputHTMLAttributes<HTMLInputElement>, "type" | "children"> & {
  children: ReactNode;
};

/**
 * 체크박스. 실제 <input type="checkbox">를 쓰고 모양만 바꿔서
 * 키보드(스페이스)·스크린 리더·폼 제출(FormData)이 기본 동작 그대로 된다.
 * 라벨 전체를 눌러도 체크되고, 누를 수 있는 높이를 44px 이상으로 둔다.
 */
export function Checkbox({ children, className = "", ...inputProps }: CheckboxProps) {
  return (
    <label className={`flex min-h-11 cursor-pointer items-center gap-2.5 ${className}`}>
      <span className="relative inline-flex size-5 shrink-0">
        <input
          type="checkbox"
          className="peer size-5 cursor-pointer appearance-none rounded-xs border border-line-strong bg-surface transition-colors checked:border-brand checked:bg-brand focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand disabled:cursor-not-allowed disabled:opacity-50"
          {...inputProps}
        />
        <Icon
          name="tick"
          size={14}
          strokeWidth={2.6}
          className="pointer-events-none absolute top-[3px] left-[3px] text-white opacity-0 peer-checked:opacity-100"
        />
      </span>
      <span className="text-sm text-ink">{children}</span>
    </label>
  );
}
