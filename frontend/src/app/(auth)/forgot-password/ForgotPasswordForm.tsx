"use client";

import Link from "next/link";
import { useEffect, useRef, useState, type FormEvent } from "react";

import { Button, buttonClassName } from "@/components/ui/Button";
import { FormAlert } from "@/components/ui/FormAlert";
import { Icon } from "@/components/ui/Icon";
import { SubmitButton } from "@/components/ui/SubmitButton";
import { TextField } from "@/components/ui/TextField";
import { clientApi } from "@/lib/api/client";
import { validateEmail } from "@/lib/auth/signupValidation";
import { NO_ERRORS, toFormErrors, type FormErrors } from "@/lib/form/formErrors";

/**
 * 비밀번호 찾기 (FR-AUTH-05).
 * 가입 여부를 알 수 없도록, 백엔드는 가입 여부와 관계없이 같은 응답(202)을 주고
 * 화면도 항상 "입력하신 이메일로 안내를 보냈어요"를 보여준다.
 */
export function ForgotPasswordForm() {
  const [email, setEmail] = useState("");
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [errors, setErrors] = useState<FormErrors<"email">>(NO_ERRORS);
  const [submitting, setSubmitting] = useState(false);
  const input = useRef<HTMLInputElement>(null);
  const sentHeading = useRef<HTMLHeadingElement>(null);

  // 폼이 안내 화면으로 바뀌면 입력란이 사라져 초점을 잃으므로 안내 제목으로 옮긴다
  useEffect(() => {
    if (sentTo) sentHeading.current?.focus();
  }, [sentTo]);

  async function send(target: string) {
    setSubmitting(true);
    setErrors(NO_ERRORS);
    try {
      await clientApi.post<void>("/api/auth/password-reset/request", { email: target });
      setSentTo(target);
    } catch (e) {
      const next = toFormErrors(e, ["email"] as const);
      setErrors(next);
      if (next.fields.email) input.current?.focus();
    } finally {
      setSubmitting(false);
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    const error = validateEmail(email);
    if (error) {
      setErrors({ fields: { email: error }, form: null });
      input.current?.focus();
      return;
    }
    await send(email.trim());
  }

  if (sentTo) {
    return (
      <div className="flex flex-col gap-5">
        <div role="status" className="rounded-card bg-brand-tint px-5 py-6 text-center">
          <Icon name="check" size={28} className="mx-auto text-brand" />
          <h2 ref={sentHeading} tabIndex={-1} className="mt-3 text-lg font-bold outline-none">
            입력하신 이메일로 안내를 보냈어요
          </h2>
          <p className="mt-1 text-sm break-all text-muted">{sentTo}</p>
          <p className="mt-3 text-sm text-ink">
            메일의 링크에서 새 비밀번호를 설정해 주세요. 링크는 30분 동안 한 번만 쓸 수 있어요.
          </p>
        </div>
        {errors.form && <FormAlert message={errors.form.message} traceId={errors.form.traceId} />}
        <Link href="/login" className={buttonClassName("primary", "w-full")}>
          로그인 화면으로
        </Link>
        <div className="flex flex-col items-center gap-1 text-sm text-muted">
          <p>메일이 오지 않았나요? 스팸함도 확인해 주세요.</p>
          <div className="flex gap-2">
            <Button variant="ghost" loading={submitting} onClick={() => send(sentTo)} className="min-h-11 px-3 text-sm">
              다시 보내기
            </Button>
            <Button
              variant="ghost"
              onClick={() => {
                setSentTo(null);
                setErrors(NO_ERRORS);
              }}
              className="min-h-11 px-3 text-sm"
            >
              이메일 다시 입력
            </Button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-5">
      {errors.form && <FormAlert message={errors.form.message} traceId={errors.form.traceId} />}
      <TextField
        ref={input}
        label="이메일"
        name="email"
        type="email"
        inputMode="email"
        autoComplete="email"
        autoCapitalize="none"
        spellCheck={false}
        placeholder="example@email.com"
        value={email}
        onChange={(e) => {
          setEmail(e.target.value);
          if (errors.fields.email) setErrors(NO_ERRORS);
        }}
        error={errors.fields.email}
      />
      <SubmitButton loading={submitting} pendingText="보내는 중…" className="w-full">
        재설정 링크 받기
      </SubmitButton>
    </form>
  );
}
