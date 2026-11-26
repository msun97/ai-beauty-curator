"use client";

import { useState, type ComponentProps } from "react";

import { Icon } from "./Icon";
import { TextField } from "./TextField";

type PasswordFieldProps = Omit<ComponentProps<typeof TextField>, "type" | "trailing">;

/**
 * 비밀번호 입력란. 오른쪽 버튼으로 입력한 글자를 보이거나 숨긴다.
 * autoComplete는 용도에 맞게 넘긴다: 로그인 "current-password", 가입·재설정 "new-password".
 */
export function PasswordField({ autoComplete = "current-password", ...props }: PasswordFieldProps) {
  const [visible, setVisible] = useState(false);

  return (
    <TextField
      {...props}
      type={visible ? "text" : "password"}
      autoComplete={autoComplete}
      autoCapitalize="none"
      spellCheck={false}
      trailing={
        <button
          type="button"
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? "비밀번호 숨기기" : "비밀번호 보기"}
          aria-pressed={visible}
          className="inline-flex size-11 items-center justify-center rounded-md text-subtle transition-colors hover:text-ink focus-visible:outline-2 focus-visible:outline-brand"
        >
          <Icon name={visible ? "eyeOff" : "eye"} size={20} />
        </button>
      }
    />
  );
}
