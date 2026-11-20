"use client";

import { useSyncExternalStore } from "react";

import { getServerToasts, getToasts, subscribeToasts, toast, type ToastType } from "@/lib/toast";

import { Icon, type IconName } from "./Icon";
import { TraceId } from "./TraceId";

/* 어두운 토스트 배경 위에서 대비가 충분한 밝은 상태색 */
const ICON: Record<ToastType, { name: IconName; className: string }> = {
  info: { name: "info", className: "text-white/80" },
  success: { name: "check", className: "text-[#7fd6a6]" },
  error: { name: "alert", className: "text-[#ff9a9a]" },
};

/**
 * 토스트 표시 영역. 루트 레이아웃에 한 번만 둔다.
 * 하단 탭 바가 있는 화면에서는 탭 바 위에 뜬다 (globals.css의 --toast-offset).
 */
export function Toaster() {
  const items = useSyncExternalStore(subscribeToasts, getToasts, getServerToasts);

  return (
    <div
      aria-live="polite"
      className="pointer-events-none fixed inset-x-0 bottom-[calc(var(--toast-offset,0px)+env(safe-area-inset-bottom)+12px)] z-50 mx-auto flex max-w-app flex-col gap-2 px-screen"
    >
      {items.map((item) => (
        <div
          key={item.id}
          role={item.type === "error" ? "alert" : "status"}
          className="toast-enter pointer-events-auto flex items-start gap-2.5 rounded-md bg-ink-strong/95 py-1 pr-1 pl-4 text-white shadow-card"
        >
          <Icon name={ICON[item.type].name} size={20} className={`mt-3 shrink-0 ${ICON[item.type].className}`} />
          <div className="flex-1 py-2.5">
            <p className="text-sm font-medium">{item.message}</p>
            <TraceId value={item.traceId} className="mt-0.5 text-white/75" />
          </div>
          <button
            type="button"
            onClick={() => toast.dismiss(item.id)}
            aria-label="알림 닫기"
            className="flex size-11 shrink-0 items-center justify-center rounded-sm text-white/75 hover:text-white focus-visible:outline-2 focus-visible:outline-white"
          >
            <Icon name="close" size={18} />
          </button>
        </div>
      ))}
    </div>
  );
}
