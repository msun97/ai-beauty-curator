import Link from "next/link";
import type { ReactNode } from "react";

import { BackButton } from "@/components/layout/BackButton";
import { Logo } from "@/components/Logo";

/**
 * 로그인·회원가입·비밀번호 찾기 화면의 공통 레이아웃.
 * 하단 탭 바 없이 뒤로 가기와 로고만 둔다.
 * 로그인한 사용자를 홈으로 보내는 일은 src/proxy.ts가 화면을 그리기 전에 처리한다.
 */
export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="mx-auto flex min-h-dvh w-full max-w-app flex-1 flex-col bg-surface">
      <header className="relative flex h-14 items-center px-screen">
        <BackButton />
        <Link
          href="/"
          className="absolute left-1/2 -translate-x-1/2 rounded-md focus-visible:outline-2 focus-visible:outline-brand"
        >
          <Logo className="text-xl" />
        </Link>
      </header>
      <div className="flex flex-1 flex-col pb-[env(safe-area-inset-bottom)]">{children}</div>
    </div>
  );
}
