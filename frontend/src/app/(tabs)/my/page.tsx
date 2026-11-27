import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Suspense } from "react";

import { LogoutButton } from "@/components/auth/LogoutButton";
import { Icon } from "@/components/ui/Icon";
import { getCurrentUser } from "@/lib/api/server";
import { loginPath } from "@/lib/auth/nextPath";

export const metadata: Metadata = { title: "마이" };

/**
 * 약관·처리방침 링크 (FIX-005).
 * 개인정보 처리방침은 이용자가 쉽게 알아볼 수 있게 굵게 표시한다 (개인정보 처리방침 작성지침).
 */
const LEGAL_LINKS = [
  { href: "/terms", label: "서비스 이용약관", strong: false },
  { href: "/privacy", label: "개인정보 처리방침", strong: true },
] as const;

/**
 * 마이 화면. 로그인해야 볼 수 있다 (proxy가 비회원을 로그인 화면으로 보냄).
 * 제목은 바로 보여주고, 내 정보는 요청마다 쿠키로 조회하므로 <Suspense> 안에서 불러온다 (cacheComponents).
 */
export default function MyPage() {
  return (
    <main className="flex flex-1 flex-col px-screen pt-8 pb-8">
      <h1 className="text-2xl font-bold">마이</h1>
      <Suspense fallback={<ProfileSkeleton />}>
        <MyProfile />
      </Suspense>
      <section aria-labelledby="my-menu" className="mt-8">
        <h2 id="my-menu" className="sr-only">
          메뉴
        </h2>
        <div className="rounded-card bg-surface-muted px-5 py-8 text-center text-sm text-muted">
          피부 프로필과 선호·기피 성분 관리는 준비하고 있어요
        </div>
      </section>
      <nav aria-labelledby="my-legal" className="mt-8">
        <h2 id="my-legal" className="mb-2 text-sm font-bold text-muted">
          약관 및 정책
        </h2>
        <ul className="divide-y divide-line rounded-card border border-line">
          {LEGAL_LINKS.map((link) => (
            <li key={link.href}>
              <Link
                href={link.href}
                className={`flex min-h-12 items-center justify-between gap-2 px-5 text-base hover:bg-surface-muted focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-brand ${link.strong ? "font-bold" : ""}`}
              >
                {link.label}
                <Icon name="chevronRight" size={18} className="text-subtle" />
              </Link>
            </li>
          ))}
        </ul>
      </nav>
    </main>
  );
}

async function MyProfile() {
  const user = await getCurrentUser();
  // 쿠키는 살아 보였지만 백엔드가 무효라고 한 경우 (proxy는 쿠키 만료 시각만 본다)
  if (!user) {
    redirect(loginPath("/my"));
  }
  return (
    <section aria-label="내 정보" className="mt-6 flex flex-col gap-4">
      <div className="flex items-center gap-4 rounded-card bg-surface px-5 py-5 shadow-card">
        <div
          aria-hidden="true"
          className="flex size-12 shrink-0 items-center justify-center rounded-full bg-brand-light font-display text-lg font-bold text-brand"
        >
          {Array.from(user.nickname)[0]?.toUpperCase()}
        </div>
        <div className="min-w-0">
          <p className="truncate text-lg font-bold">{user.nickname}</p>
          <p className="truncate text-sm text-muted">{user.email}</p>
        </div>
      </div>
      <LogoutButton />
    </section>
  );
}

function ProfileSkeleton() {
  return (
    <div aria-busy="true" aria-label="내 정보를 불러오는 중" className="mt-6 flex flex-col gap-4">
      <div className="h-[88px] animate-pulse rounded-card bg-surface-muted motion-reduce:animate-none" />
      <div className="h-12 animate-pulse rounded-md bg-surface-muted motion-reduce:animate-none" />
    </div>
  );
}
