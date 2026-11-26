import type { Metadata } from "next";
import Link from "next/link";

import { AuthScreen } from "@/components/auth/AuthScreen";

import { SignupForm } from "./SignupForm";

export const metadata: Metadata = { title: "회원가입" };

export default function SignupPage() {
  return (
    <AuthScreen title="회원가입" description="가입하고 내 피부에 맞는 제품을 찾아보세요.">
      <SignupForm />
      <p className="mt-6 text-center text-sm text-muted">
        이미 계정이 있나요?{" "}
        <Link href="/login" className="inline-flex min-h-11 items-center font-bold text-brand hover:text-brand-dark">
          로그인
        </Link>
      </p>
    </AuthScreen>
  );
}
