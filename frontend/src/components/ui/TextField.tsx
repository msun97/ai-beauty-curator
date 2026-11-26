"use client";

import { useId, type InputHTMLAttributes, type ReactNode, type Ref } from "react";

type TextFieldProps = Omit<InputHTMLAttributes<HTMLInputElement>, "id"> & {
  label: string;
  /** 입력값 오류. 있으면 빨간 테두리와 함께 입력란 아래에 표시한다 (ApiError.fieldMessage 결과 등) */
  error?: string;
  /** 오류가 없을 때 보여줄 도움말 (예: 비밀번호 규칙) */
  hint?: string;
  /** 입력란 오른쪽 안에 넣을 요소 (예: 비밀번호 보기 버튼, 글자 수) */
  trailing?: ReactNode;
  /** 입력란 아래, 오류·도움말 다음에 넣을 요소 (예: 비밀번호 규칙 체크 목록) */
  children?: ReactNode;
  ref?: Ref<HTMLInputElement>;
};

/**
 * 라벨·입력란·오류 문구를 묶은 입력 필드 (NFR-UX-03).
 * 스크린 리더가 오류를 읽을 수 있도록 aria-invalid, aria-describedby를 연결한다.
 */
export function TextField({
  label,
  error,
  hint,
  trailing,
  children,
  className = "",
  "aria-describedby": extraDescribedBy,
  ...inputProps
}: TextFieldProps) {
  const id = useId();
  const messageId = `${id}-message`;
  const message = error ?? hint;
  // 오류·도움말과, 밖에서 넘긴 설명(예: 비밀번호 규칙 목록)을 함께 읽어 주도록 연결한다
  const describedBy = [message ? messageId : null, extraDescribedBy].filter(Boolean).join(" ") || undefined;

  return (
    <div className={`flex flex-col gap-1.5 ${className}`}>
      <label htmlFor={id} className="text-sm font-medium text-ink">
        {label}
      </label>
      <div className="relative">
        <input
          id={id}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={`h-12 w-full rounded-md border bg-surface px-4 text-base text-ink transition-colors placeholder:text-muted focus:ring-2 focus:outline-none disabled:bg-surface-muted disabled:text-muted ${
            trailing ? "pr-12" : ""
          } ${error ? "border-danger focus:ring-danger/25" : "border-line-strong focus:border-brand focus:ring-brand/20"}`}
          {...inputProps}
        />
        {trailing && <div className="absolute inset-y-0 right-1 flex items-center">{trailing}</div>}
      </div>
      {message && (
        <p id={messageId} className={`text-xs ${error ? "text-danger" : "text-muted"}`}>
          {message}
        </p>
      )}
      {children}
    </div>
  );
}
