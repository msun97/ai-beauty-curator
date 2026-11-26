"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useId, useRef, useState, useSyncExternalStore, type FormEvent } from "react";

import { buttonClassName } from "@/components/ui/Button";
import { FormAlert } from "@/components/ui/FormAlert";
import { Icon } from "@/components/ui/Icon";
import { PasswordField } from "@/components/ui/PasswordField";
import { PasswordRules } from "@/components/ui/PasswordRules";
import { SubmitButton } from "@/components/ui/SubmitButton";
import { clientApi } from "@/lib/api/client";
import { isPasswordTooLong, isValidPassword, PASSWORD_MAX_LENGTH, PASSWORD_RULE_MESSAGE } from "@/lib/auth/passwordRule";
import { NO_ERRORS, firstErrorField, toFormErrors, type FieldErrors, type FormErrors } from "@/lib/form/formErrors";
import { toast } from "@/lib/toast";

/** 백엔드 요청 필드명과 같게 둔다 (서버 fieldErrors가 그대로 입력란에 붙도록) */
const FIELDS = ["newPassword", "passwordConfirm"] as const;
type Field = (typeof FIELDS)[number];

const noopSubscribe = () => () => {};
/** 주소의 ?token= 값. 서버에서 그릴 때는 알 수 없으므로 undefined(확인 중) */
function useResetToken(): string | null | undefined {
  return useSyncExternalStore(
    noopSubscribe,
    () => new URLSearchParams(window.location.search).get("token"),
    () => undefined,
  );
}

function validate(newPassword: string, passwordConfirm: string): FieldErrors<Field> {
  const errors: FieldErrors<Field> = {};
  if (!newPassword) errors.newPassword = "새 비밀번호를 입력해 주세요.";
  else if (isPasswordTooLong(newPassword)) errors.newPassword = `비밀번호는 ${PASSWORD_MAX_LENGTH}자 이하로 입력해 주세요.`;
  else if (!isValidPassword(newPassword)) errors.newPassword = PASSWORD_RULE_MESSAGE;
  if (!passwordConfirm) errors.passwordConfirm = "비밀번호를 한 번 더 입력해 주세요.";
  else if (passwordConfirm !== newPassword) errors.passwordConfirm = "비밀번호가 일치하지 않아요.";
  return errors;
}

/**
 * 새 비밀번호 설정 (FR-AUTH-05). 메일의 링크(/reset-password?token=…)로 들어온다.
 * - 링크가 만료됐거나 이미 썼으면(INVALID_RESET_TOKEN) 비밀번호 찾기를 다시 하도록 안내한다.
 * - 성공하면 모든 기기에서 로그아웃되므로, 로그인 화면으로 보내고 토스트로 알린다.
 */
export function ResetPasswordForm() {
  const router = useRouter();
  const token = useResetToken();
  const [newPassword, setNewPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [errors, setErrors] = useState<FormErrors<Field>>(NO_ERRORS);
  const [submitting, setSubmitting] = useState(false);
  const [expired, setExpired] = useState(false);
  const inputs = useRef<Partial<Record<Field, HTMLInputElement | null>>>({});
  const rulesId = useId();

  if (token === undefined) {
    return <div className="h-64" aria-busy="true" />;
  }
  if (!token || expired) {
    return <InvalidLink />;
  }

  function focusFirstError(fields: FieldErrors<Field>) {
    const field = firstErrorField(FIELDS, fields);
    if (field) inputs.current[field]?.focus();
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    const clientErrors = validate(newPassword, passwordConfirm);
    if (Object.keys(clientErrors).length > 0) {
      setErrors({ fields: clientErrors, form: null });
      focusFirstError(clientErrors);
      return;
    }
    setSubmitting(true);
    setErrors(NO_ERRORS);
    try {
      await clientApi.post<void>("/api/auth/password-reset/confirm", { token, newPassword });
    } catch (e) {
      const next = toFormErrors(e, FIELDS);
      if (next.form?.code === "INVALID_RESET_TOKEN") {
        setExpired(true);
        return;
      }
      setErrors(next);
      setSubmitting(false);
      focusFirstError(next.fields);
      return;
    }
    toast.success("비밀번호를 바꿨어요. 새 비밀번호로 로그인해 주세요.");
    router.replace("/login");
    router.refresh();
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-5">
      {errors.form && <FormAlert message={errors.form.message} traceId={errors.form.traceId} />}
      <PasswordField
        ref={(el) => {
          inputs.current.newPassword = el;
        }}
        label="새 비밀번호"
        name="newPassword"
        autoComplete="new-password"
        value={newPassword}
        onChange={(e) => {
          setNewPassword(e.target.value);
          if (errors.fields.newPassword) setErrors((p) => ({ ...p, fields: { ...p.fields, newPassword: undefined } }));
        }}
        error={errors.fields.newPassword}
        aria-describedby={rulesId}
      >
        <PasswordRules id={rulesId} value={newPassword} />
      </PasswordField>
      <PasswordField
        ref={(el) => {
          inputs.current.passwordConfirm = el;
        }}
        label="새 비밀번호 확인"
        name="passwordConfirm"
        autoComplete="new-password"
        value={passwordConfirm}
        onChange={(e) => {
          setPasswordConfirm(e.target.value);
          if (errors.fields.passwordConfirm)
            setErrors((p) => ({ ...p, fields: { ...p.fields, passwordConfirm: undefined } }));
        }}
        error={errors.fields.passwordConfirm}
        hint={passwordConfirm && passwordConfirm === newPassword ? "비밀번호가 일치해요." : undefined}
      />
      <p className="text-xs text-muted">비밀번호를 바꾸면 로그인된 모든 기기에서 로그아웃돼요.</p>
      <SubmitButton loading={submitting} pendingText="변경하는 중…" className="w-full">
        비밀번호 변경
      </SubmitButton>
    </form>
  );
}

/** 링크가 없거나, 만료됐거나, 이미 사용한 경우 */
function InvalidLink() {
  const heading = useRef<HTMLHeadingElement>(null);
  // 제출 후 이 안내로 바뀌면 폼이 사라져 초점을 잃으므로 제목으로 옮긴다
  useEffect(() => {
    heading.current?.focus();
  }, []);
  return (
    <div className="flex flex-col gap-5">
      <div role="alert" className="rounded-card bg-surface-muted px-5 py-6 text-center">
        <Icon name="alert" size={28} className="mx-auto text-danger" />
        <h2 ref={heading} tabIndex={-1} className="mt-3 text-lg font-bold outline-none">
          링크를 사용할 수 없어요
        </h2>
        <p className="mt-2 text-sm text-ink">
          링크가 만료됐거나 이미 사용됐어요. 비밀번호 찾기를 다시 진행해 주세요.
        </p>
      </div>
      <Link href="/forgot-password" className={buttonClassName("primary", "w-full")}>
        비밀번호 찾기 다시 하기
      </Link>
      <Link href="/login" className={buttonClassName("secondary", "w-full")}>
        로그인 화면으로
      </Link>
    </div>
  );
}
