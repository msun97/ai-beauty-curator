import type { Metadata } from "next";
import Link from "next/link";

import { AuthScreen } from "@/components/auth/AuthScreen";

import { LoginForm } from "./LoginForm";

export const metadata: Metadata = { title: "로그인" };

export default function LoginPage() {
  return (
    <AuthScreen title="로그인" description="내 피부에 맞는지 확인하려면 로그인해 주세요.">
      <LoginForm />
      <nav aria-label="계정 도움" className="mt-6 flex items-center justify-center gap-3 text-sm text-muted">
        <Link href="/forgot-password" className="inline-flex min-h-11 items-center px-1 hover:text-ink">
          비밀번호 찾기
        </Link>
        <span aria-hidden="true" className="text-line-strong">
          |
        </span>
        <Link href="/signup" className="inline-flex min-h-11 items-center px-1 hover:text-ink">
          회원가입
        </Link>
      </nav>
      <Link
        href="/"
        className="mt-auto inline-flex min-h-11 items-center justify-center self-center pt-6 text-sm text-muted underline underline-offset-4 hover:text-ink"
      >
        로그인 없이 둘러보기
      </Link>
    </AuthScreen>
  );
}
