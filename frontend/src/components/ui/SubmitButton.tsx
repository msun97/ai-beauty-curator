"use client";

import { useFormStatus } from "react-dom";

import { Button, type ButtonProps } from "./Button";

/**
 * 폼 제출 버튼. 자신이 속한 <form>이 제출 중이면 자동으로 로딩 상태가 되어 중복 제출을 막는다.
 * 제출 중 글자를 바꾸고 싶으면 pendingText를 넘긴다 (예: "로그인 중…").
 */
export function SubmitButton({
  pendingText,
  children,
  loading,
  ...props
}: Omit<ButtonProps, "type"> & { pendingText?: string }) {
  const { pending } = useFormStatus();
  const busy = pending || loading;
  return (
    <Button type="submit" loading={busy} {...props}>
      {busy && pendingText ? pendingText : children}
    </Button>
  );
}
