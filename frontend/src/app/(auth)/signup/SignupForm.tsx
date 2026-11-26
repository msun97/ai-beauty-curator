"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useId, useRef, useState, type FormEvent } from "react";

import { AgreementList } from "@/components/ui/AgreementList";
import { Button, buttonClassName } from "@/components/ui/Button";
import { FormAlert } from "@/components/ui/FormAlert";
import { Icon } from "@/components/ui/Icon";
import { PasswordField } from "@/components/ui/PasswordField";
import { PasswordRules } from "@/components/ui/PasswordRules";
import { SubmitButton } from "@/components/ui/SubmitButton";
import { TextField } from "@/components/ui/TextField";
import { SIGNUP_AGREEMENTS } from "@/lib/auth/agreements";
import { safeNextPath } from "@/lib/auth/nextPath";
import { signupApi } from "@/lib/auth/signupApi";
import {
  DETAIL_FIELDS,
  EMPTY_DETAILS,
  NICKNAME_MAX_LENGTH,
  VERIFICATION_CODE_LENGTH,
  normalizeCode,
  toSignupRequest,
  validateCode,
  validateDetails,
  validateEmail,
  type DetailField,
  type DetailValues,
} from "@/lib/auth/signupValidation";
import { NO_ERRORS, firstErrorField, toFormErrors, type FieldErrors, type FormErrors } from "@/lib/form/formErrors";

/** 인증 코드를 다시 보낼 수 있을 때까지 기다리는 시간 (백엔드 재발송 간격과 같게) */
const RESEND_COOLDOWN_SECONDS = 60;

type Step =
  | { name: "email" }
  | { name: "code"; email: string }
  | { name: "registered"; email: string }
  | { name: "details"; email: string; verificationToken: string };

/**
 * 회원가입 (FR-AUTH-01): ① 이메일 → ② 인증 코드 → ③ 비밀번호·닉네임·약관.
 *
 * 가입 여부가 드러나지 않도록, 코드 요청은 가입 여부와 관계없이 항상 같은 안내를 보여주고
 * 메일로 받은 코드를 맞힌 뒤에만 "이미 가입된 이메일이에요"를 보여준다.
 * (백엔드 API는 수정 요청 FIX-008의 계약대로 호출한다)
 */
export function SignupForm() {
  const [step, setStep] = useState<Step>({ name: "email" });
  // 처음 열 때는 초점을 옮기지 않고, 코드 단계에서 "이메일 다시 입력"으로 돌아왔을 때만 이메일 칸으로 옮긴다
  const [returnedToEmail, setReturnedToEmail] = useState(false);

  switch (step.name) {
    case "email":
      return <EmailStep autoFocus={returnedToEmail} onSent={(email) => setStep({ name: "code", email })} />;
    case "code":
      return (
        <CodeStep
          email={step.email}
          onChangeEmail={() => {
            setReturnedToEmail(true);
            setStep({ name: "email" });
          }}
          onVerified={(result) =>
            setStep(
              result.registered
                ? { name: "registered", email: step.email }
                : { name: "details", email: step.email, verificationToken: result.verificationToken },
            )
          }
        />
      );
    case "registered":
      return <RegisteredNotice email={step.email} />;
    case "details":
      return <DetailsStep email={step.email} verificationToken={step.verificationToken} />;
  }
}

// --- 단계 표시 -----------------------------------------------------------------

function StepHeader({ current, title, description }: { current: 1 | 2 | 3; title: string; description?: string }) {
  return (
    <div>
      <p className="font-display text-xs font-bold text-brand">
        {current} / 3<span className="sr-only">단계</span>
      </p>
      <h2 className="mt-1 text-lg font-bold">{title}</h2>
      {description && <p className="mt-1 text-sm text-muted">{description}</p>}
    </div>
  );
}

// --- ① 이메일 -------------------------------------------------------------------

function EmailStep({ autoFocus, onSent }: { autoFocus: boolean; onSent: (email: string) => void }) {
  const [email, setEmail] = useState("");
  const [errors, setErrors] = useState<FormErrors<"email">>(NO_ERRORS);
  const [submitting, setSubmitting] = useState(false);
  const input = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (autoFocus) input.current?.focus();
  }, [autoFocus]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    const error = validateEmail(email);
    if (error) {
      setErrors({ fields: { email: error }, form: null });
      input.current?.focus();
      return;
    }
    setSubmitting(true);
    setErrors(NO_ERRORS);
    try {
      await signupApi.requestCode(email.trim());
    } catch (e) {
      const next = toFormErrors(e, ["email"] as const);
      setErrors(next);
      setSubmitting(false);
      if (next.fields.email) input.current?.focus();
      return;
    }
    onSent(email.trim());
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-5">
      <StepHeader current={1} title="이메일을 입력해 주세요" description="입력한 이메일로 인증 코드를 보내드려요." />
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
        인증 코드 받기
      </SubmitButton>
    </form>
  );
}

// --- ② 인증 코드 ----------------------------------------------------------------

function CodeStep({
  email,
  onChangeEmail,
  onVerified,
}: {
  email: string;
  onChangeEmail: () => void;
  onVerified: (result: Awaited<ReturnType<typeof signupApi.confirmCode>>) => void;
}) {
  const [code, setCode] = useState("");
  const [errors, setErrors] = useState<FormErrors<"code">>(NO_ERRORS);
  const [submitting, setSubmitting] = useState(false);
  const [cooldown, setCooldown] = useState(RESEND_COOLDOWN_SECONDS);
  const [resending, setResending] = useState(false);
  const [resent, setResent] = useState(false);
  const input = useRef<HTMLInputElement>(null);

  useEffect(() => {
    input.current?.focus();
  }, []);

  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setTimeout(() => setCooldown((s) => s - 1), 1000);
    return () => clearTimeout(timer);
  }, [cooldown]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    const error = validateCode(code);
    if (error) {
      setErrors({ fields: { code: error }, form: null });
      input.current?.focus();
      return;
    }
    setSubmitting(true);
    setErrors(NO_ERRORS);
    try {
      onVerified(await signupApi.confirmCode(email, code));
    } catch (e) {
      const next = toFormErrors(e, ["code"] as const);
      setErrors(next);
      setSubmitting(false);
      input.current?.focus();
    }
  }

  async function resend() {
    if (cooldown > 0 || resending) return;
    setResending(true);
    setErrors(NO_ERRORS);
    try {
      await signupApi.requestCode(email);
      setCode("");
      setResent(true);
      setCooldown(RESEND_COOLDOWN_SECONDS);
      input.current?.focus();
    } catch (e) {
      setErrors(toFormErrors(e, ["code"] as const));
    } finally {
      setResending(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-5">
      <StepHeader current={2} title="인증 코드를 입력해 주세요" />
      {/* 가입 여부와 관계없이 항상 같은 안내 */}
      <div role="status" className="flex gap-2 rounded-md bg-brand-tint px-4 py-3 text-sm text-ink">
        <Icon name="check" size={18} className="mt-0.5 shrink-0 text-brand" />
        <p>
          <strong className="font-bold break-all">{email}</strong>
          (으)로 인증 코드를 보냈어요. 메일이 보이지 않으면 스팸함도 확인해 주세요.
          {resent && <span className="mt-1 block text-muted">새 코드를 보냈어요. 이전 코드는 쓸 수 없어요.</span>}
        </p>
      </div>
      {errors.form && <FormAlert message={errors.form.message} traceId={errors.form.traceId} />}
      <TextField
        ref={input}
        label="인증 코드"
        name="code"
        inputMode="numeric"
        autoComplete="one-time-code"
        maxLength={VERIFICATION_CODE_LENGTH + 2}
        placeholder={`숫자 ${VERIFICATION_CODE_LENGTH}자리`}
        className="[&_input:not(:placeholder-shown)]:font-display [&_input:not(:placeholder-shown)]:tracking-[0.3em]"
        value={code}
        onChange={(e) => {
          setCode(normalizeCode(e.target.value));
          if (errors.fields.code) setErrors(NO_ERRORS);
        }}
        error={errors.fields.code}
        hint="코드는 10분 동안 쓸 수 있어요."
      />
      <SubmitButton loading={submitting} pendingText="확인하는 중…" className="w-full">
        확인
      </SubmitButton>
      <div className="flex items-center justify-between text-sm">
        <button
          type="button"
          onClick={onChangeEmail}
          className="inline-flex min-h-11 items-center px-1 text-muted underline-offset-4 hover:text-ink hover:underline"
        >
          이메일 다시 입력
        </button>
        <Button
          variant="ghost"
          onClick={resend}
          loading={resending}
          disabled={cooldown > 0}
          className="min-h-11 px-3 text-sm"
        >
          {cooldown > 0 ? `코드 다시 받기 (${cooldown}초)` : "코드 다시 받기"}
        </Button>
      </div>
    </form>
  );
}

// --- 이미 가입된 이메일 (코드를 맞힌 사람에게만) ------------------------------

function RegisteredNotice({ email }: { email: string }) {
  const heading = useRef<HTMLHeadingElement>(null);
  // 단계가 바뀌면 이전 입력란이 사라져 초점을 잃으므로 결과 제목으로 옮긴다 (키보드·스크린 리더 사용자)
  useEffect(() => {
    heading.current?.focus();
  }, []);
  return (
    <div className="flex flex-col gap-5">
      <div role="status" className="rounded-card bg-surface-muted px-5 py-6 text-center">
        <Icon name="info" size={28} className="mx-auto text-brand" />
        <h2 ref={heading} tabIndex={-1} className="mt-3 text-lg font-bold outline-none">
          이미 가입된 이메일이에요
        </h2>
        <p className="mt-1 text-sm break-all text-muted">{email}</p>
        <p className="mt-3 text-sm text-ink">이 이메일로 로그인하거나, 비밀번호가 기억나지 않으면 재설정해 주세요.</p>
      </div>
      <Link href="/login" className={buttonClassName("primary", "w-full")}>
        로그인하기
      </Link>
      <Link href="/forgot-password" className={buttonClassName("secondary", "w-full")}>
        비밀번호 찾기
      </Link>
    </div>
  );
}

// --- ③ 비밀번호·닉네임·약관 -----------------------------------------------------

function DetailsStep({ email, verificationToken }: { email: string; verificationToken: string }) {
  const router = useRouter();
  const [values, setValues] = useState<DetailValues>(EMPTY_DETAILS);
  const [errors, setErrors] = useState<FormErrors<DetailField>>(NO_ERRORS);
  const [submitting, setSubmitting] = useState(false);
  const inputs = useRef<Partial<Record<DetailField, HTMLElement | null>>>({});
  const rulesId = useId();

  // 단계가 바뀌면 이전 입력란(인증 코드)이 사라져 초점을 잃으므로 첫 입력란으로 옮긴다
  useEffect(() => {
    inputs.current.password?.focus();
  }, []);

  function update<K extends keyof DetailValues>(key: K, value: DetailValues[K]) {
    setValues((prev) => ({ ...prev, [key]: value }));
    // 고친 항목의 오류는 지워 다시 입력하는 동안 빨간 표시가 남지 않게 한다
    if (errors.fields[key]) {
      setErrors((prev) => ({ ...prev, fields: { ...prev.fields, [key]: undefined } }));
    }
  }

  function focusFirstError(fields: FieldErrors<DetailField>) {
    const field = firstErrorField(DETAIL_FIELDS, fields);
    if (field) inputs.current[field]?.focus();
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    const clientErrors = validateDetails(values);
    if (Object.keys(clientErrors).length > 0) {
      setErrors({ fields: clientErrors, form: null });
      focusFirstError(clientErrors);
      return;
    }
    setSubmitting(true);
    setErrors(NO_ERRORS);
    try {
      await signupApi.signup(toSignupRequest(verificationToken, values));
    } catch (e) {
      const next = toFormErrors(e, DETAIL_FIELDS);
      setErrors(next);
      setSubmitting(false);
      focusFirstError(next.fields);
      return;
    }
    // 가입하면 백엔드가 로그인 쿠키를 준다. 원래 보던 화면(?next=) 또는 홈으로
    router.replace(safeNextPath(new URLSearchParams(window.location.search).get("next")));
    router.refresh();
  }

  const nicknameLength = values.nickname.length;

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-5">
      <StepHeader current={3} title="가입 정보를 입력해 주세요" />
      {errors.form && <FormAlert message={errors.form.message} traceId={errors.form.traceId} />}

      {/* 인증한 이메일. 비밀번호 관리자가 계정 이름으로 저장할 수 있게 숨은 입력란을 둔다 */}
      <div className="flex items-center gap-2 rounded-md bg-surface-muted px-4 py-3 text-sm">
        <Icon name="check" size={18} className="shrink-0 text-success" />
        <span className="break-all">{email}</span>
        <span className="ml-auto shrink-0 text-xs text-success">인증 완료</span>
      </div>
      <input type="email" name="email" value={email} autoComplete="username" readOnly hidden />

      <PasswordField
        ref={(el) => {
          inputs.current.password = el;
        }}
        label="비밀번호"
        name="password"
        autoComplete="new-password"
        value={values.password}
        onChange={(e) => update("password", e.target.value)}
        error={errors.fields.password}
        aria-describedby={rulesId}
      >
        <PasswordRules id={rulesId} value={values.password} />
      </PasswordField>

      <PasswordField
        ref={(el) => {
          inputs.current.passwordConfirm = el;
        }}
        label="비밀번호 확인"
        name="passwordConfirm"
        autoComplete="new-password"
        value={values.passwordConfirm}
        onChange={(e) => update("passwordConfirm", e.target.value)}
        error={errors.fields.passwordConfirm}
        hint={values.passwordConfirm && values.passwordConfirm === values.password ? "비밀번호가 일치해요." : undefined}
      />

      <TextField
        ref={(el) => {
          inputs.current.nickname = el;
        }}
        label="닉네임 (선택)"
        name="nickname"
        autoComplete="nickname"
        placeholder="비워 두면 이메일 앞부분으로 정해져요"
        value={values.nickname}
        onChange={(e) => update("nickname", e.target.value)}
        error={errors.fields.nickname}
        trailing={
          <span
            className={`pr-3 font-display text-xs tabular-nums ${nicknameLength > NICKNAME_MAX_LENGTH ? "text-danger" : "text-muted"}`}
          >
            {nicknameLength}/{NICKNAME_MAX_LENGTH}
            <span className="sr-only">자</span>
          </span>
        }
      />

      <div
        ref={(el) => {
          inputs.current.agreements = el;
        }}
        tabIndex={-1}
        className="outline-none"
      >
        <AgreementList
          items={SIGNUP_AGREEMENTS}
          value={values.agreements}
          onChange={(next) => update("agreements", next)}
          error={errors.fields.agreements}
        />
      </div>

      <SubmitButton loading={submitting} pendingText="가입하는 중…" className="mt-1 w-full">
        가입하기
      </SubmitButton>
    </form>
  );
}
