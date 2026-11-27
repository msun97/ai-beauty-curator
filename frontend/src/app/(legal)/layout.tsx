import Link from "next/link";
import type { ReactNode } from "react";

import { BackButton } from "@/components/layout/BackButton";
import { Logo } from "@/components/Logo";

/**
 * 이용약관·개인정보 처리방침 화면의 공통 레이아웃 (FIX-005).
 * 로그인 여부와 관계없이 누구나 볼 수 있다 (proxy 대상 아님). 하단 탭 바 없이 뒤로 가기와 로고만 둔다.
 */
export default function LegalLayout({ children }: { children: ReactNode }) {
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
      <div className="flex flex-1 flex-col pb-[calc(env(safe-area-inset-bottom)+40px)]">{children}</div>
    </div>
  );
}
