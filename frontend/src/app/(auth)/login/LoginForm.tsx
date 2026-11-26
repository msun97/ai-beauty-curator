"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRef, useState, type FormEvent } from "react";

import { buttonClassName } from "@/components/ui/Button";
import { FormAlert } from "@/components/ui/FormAlert";
import { Icon } from "@/components/ui/Icon";
import { PasswordField } from "@/components/ui/PasswordField";
import { SubmitButton } from "@/components/ui/SubmitButton";
import { TextField } from "@/components/ui/TextField";
import { TraceId } from "@/components/ui/TraceId";
import { clientApi } from "@/lib/api/client";
import type { AuthUser } from "@/lib/api/types";
import { safeNextPath } from "@/lib/auth/nextPath";
import { NO_ERRORS, firstErrorField, toFormErrors, type FieldErrors, type FormErrors } from "@/lib/form/formErrors";

const FIELDS = ["email", "password"] as const;
type Field = (typeof FIELDS)[number];

/** 입력 즉시 안내할 수 있는 것만 화면에서 검사한다. 형식·규칙의 최종 판단은 백엔드가 한다. */
function validate(email: string, password: string): FieldErrors<Field> {
  const errors: FieldErrors<Field> = {};
  if (!email.trim()) errors.email = "이메일을 입력해 주세요.";
  if (!password) errors.password = "비밀번호를 입력해 주세요.";
  return errors;
}

/**
 * 로그인 폼 (FR-AUTH-02).
 * - 실패 원인을 특정하지 않는 문구를 그대로 보여준다(INVALID_CREDENTIALS).
 * - 5회 연속 실패로 잠기면(LOGIN_LOCKED) 잠금 안내와 비밀번호 재설정 버튼을 보여준다.
 * - 성공하면 ?next=로 받은 원래 화면으로, 없거나 안전하지 않으면 홈으로 간다.
 */
export function LoginForm() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [errors, setErrors] = useState<FormErrors<Field>>(NO_ERRORS);
  const [submitting, setSubmitting] = useState(false);
  const inputs = useRef<Partial<Record<Field, HTMLInputElement | null>>>({});

  function focusFirstError(fields: FieldErrors<Field>) {
    const field = firstErrorField(FIELDS, fields);
    if (field) inputs.current[field]?.focus();
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;

    const clientErrors = validate(email, password);
    if (Object.keys(clientErrors).length > 0) {
      setErrors({ fields: clientErrors, form: null });
      focusFirstError(clientErrors);
      return;
    }

    setSubmitting(true);
    setErrors(NO_ERRORS);
    try {
      await clientApi.post<AuthUser>("/api/auth/login", { email: email.trim(), password });
    } catch (e) {
      const next = toFormErrors(e, FIELDS);
      setErrors(next);
      setSubmitting(false);
      focusFirstError(next.fields);
      return;
    }

    // 주소창의 next는 제출 시점에 읽는다 (페이지를 정적으로 유지하기 위해 useSearchParams를 쓰지 않음)
    const next = safeNextPath(new URLSearchParams(window.location.search).get("next"));
    router.replace(next);
    // 로그인 쿠키가 생겼으니 서버 화면을 다시 그려 로그인 상태를 반영한다
    router.refresh();
  }

  const locked = errors.form?.code === "LOGIN_LOCKED";

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
      {errors.form &&
        (locked ? (
          <LockedAlert message={errors.form.message} traceId={errors.form.traceId} />
        ) : (
          <FormAlert message={errors.form.message} traceId={errors.form.traceId} />
        ))}

      <TextField
        ref={(el) => {
          inputs.current.email = el;
        }}
        label="이메일"
        name="email"
        type="email"
        inputMode="email"
        autoComplete="username"
        autoCapitalize="none"
        spellCheck={false}
        placeholder="example@email.com"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        error={errors.fields.email}
      />
      <PasswordField
        ref={(el) => {
          inputs.current.password = el;
        }}
        label="비밀번호"
        name="password"
        autoComplete="current-password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        error={errors.fields.password}
      />

      <SubmitButton loading={submitting} pendingText="로그인 중…" className="mt-2 w-full">
        로그인
      </SubmitButton>
    </form>
  );
}

/** 로그인 제한 안내. 문구는 백엔드가 설정값(횟수·시간)으로 만들어 내려준다. */
function LockedAlert({ message, traceId }: { message: string; traceId: string | null }) {
  return (
    <div role="alert" className="flex flex-col gap-3 rounded-md bg-danger-bg px-4 py-3 text-danger">
      <div className="flex gap-2">
        <Icon name="alert" size={18} className="mt-0.5 shrink-0" />
        <div>
          <p className="text-sm font-medium">{message}</p>
          <TraceId value={traceId} className="mt-0.5" />
        </div>
      </div>
      <Link href="/forgot-password" className={buttonClassName("secondary", "min-h-11 w-full text-sm")}>
        비밀번호 재설정하기
      </Link>
    </div>
  );
}
