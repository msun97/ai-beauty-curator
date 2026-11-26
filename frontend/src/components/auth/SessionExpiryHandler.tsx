"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef } from "react";

import { onSessionExpired } from "@/lib/api/client";
import { loginPath } from "@/lib/auth/nextPath";
import { isGuestOnlyPath } from "@/lib/auth/routes";
import { toast } from "@/lib/toast";

/** 여러 요청이 동시에 만료를 알려도 한 번만 처리한다 */
const DEDUPE_MS = 3_000;

/**
 * 쓰다가 로그인이 만료되면(토큰 재발급까지 실패) 로그인 화면으로 보낸다.
 * 로그인 후 보던 화면으로 돌아올 수 있게 지금 주소를 ?next=로 넘긴다. 루트 레이아웃에 한 번만 둔다.
 */
export function SessionExpiryHandler() {
  const router = useRouter();
  const lastHandled = useRef(0);

  useEffect(
    () =>
      onSessionExpired(() => {
        const now = Date.now();
        if (now - lastHandled.current < DEDUPE_MS) return;
        lastHandled.current = now;

        const { pathname, search } = window.location;
        if (isGuestOnlyPath(pathname)) return;
        toast.info("로그인이 만료됐어요. 다시 로그인해 주세요.");
        router.replace(loginPath(`${pathname}${search}`));
        router.refresh();
      }),
    [router],
  );

  return null;
}
