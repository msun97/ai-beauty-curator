import type { Metadata } from "next";
import Link from "next/link";

import { AuthScreen, FormPlaceholder } from "@/components/auth/AuthScreen";

export const metadata: Metadata = { title: "로그인" };

export default function LoginPage() {
  return (
    <AuthScreen title="로그인" description="내 피부에 맞는지 확인하려면 로그인해 주세요.">
      <FormPlaceholder />
      <nav className="mt-6 flex justify-center gap-4 text-sm text-muted">
        <Link href="/forgot-password" className="hover:text-ink">
          비밀번호 찾기
        </Link>
        <span aria-hidden="true">|</span>
        <Link href="/signup" className="hover:text-ink">
          회원가입
        </Link>
      </nav>
    </AuthScreen>
  );
}
