"use client";

import { useRouter } from "next/navigation";

import { Icon } from "@/components/ui/Icon";

/**
 * 이전 화면으로 돌아가는 버튼.
 * 메일 링크나 주소 직접 입력으로 들어와 돌아갈 기록이 없으면 fallback 주소로 보낸다.
 */
export function BackButton({ fallback = "/" }: { fallback?: string }) {
  const router = useRouter();

  function goBack() {
    if (window.history.length > 1) {
      router.back();
    } else {
      router.replace(fallback);
    }
  }

  return (
    <button
      type="button"
      onClick={goBack}
      aria-label="뒤로 가기"
      className="-ml-2 inline-flex size-11 items-center justify-center rounded-md text-ink transition-colors hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-brand"
    >
      <Icon name="chevronLeft" size={24} />
    </button>
  );
}
