"use client";

import { useEffect, useId, useRef, type ReactNode } from "react";

import { Icon } from "./Icon";

type BottomSheetProps = {
  open: boolean;
  /** 닫기 버튼, 바깥(배경) 누르기, Esc 키 — 어떤 방법으로 닫아도 한 번 호출된다. 여기서 open을 false로 바꾼다 */
  onClose: () => void;
  title: string;
  children: ReactNode;
  /** 시트 아래에 고정되는 영역(확인 버튼 등). 내용이 길어 스크롤해도 항상 보인다 */
  footer?: ReactNode;
};

/**
 * 화면 아래에서 올라오는 시트 (약관 "보기" 등).
 *
 * 브라우저 기본 <dialog>의 showModal()을 써서 접근성 동작을 직접 만들지 않는다.
 * - 열려 있는 동안 뒤 화면은 클릭·Tab 이동·스크린 리더에서 제외되고(inert), Esc로 닫힌다.
 * - 닫히면 시트를 연 버튼으로 포커스가 돌아간다.
 * - 열리면 본문 영역에 포커스를 둬서, 키보드 사용자가 화살표·Page Down으로 바로 스크롤할 수 있다.
 * 뒤 화면 스크롤 잠금은 globals.css의 html:has(dialog[open]:modal)이 맡는다.
 */
export function BottomSheet({ open, onClose, title, children, footer }: BottomSheetProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const bodyRef = useRef<HTMLDivElement>(null);
  const titleId = useId();

  // 닫기 버튼·배경 클릭도 dialog.close()로 닫아, close 이벤트 한 곳에서만 onClose가 불리게 한다
  const close = () => dialogRef.current?.close();

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (open && !dialog.open) {
      dialog.showModal();
      bodyRef.current?.scrollTo({ top: 0 });
      bodyRef.current?.focus();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby={titleId}
      // Esc·dialog.close() 모두 close 이벤트를 낸다. 부모 상태를 닫힘으로 맞춘다
      onClose={onClose}
      // 시트 바깥(배경)을 누르면 이벤트 대상이 <dialog> 자신이 된다
      onClick={(e) => {
        if (e.target === e.currentTarget) close();
      }}
      className="sheet-enter m-0 mx-auto mt-auto max-h-[85dvh] w-full max-w-app overflow-hidden rounded-t-card bg-surface p-0 text-ink shadow-card backdrop:bg-ink-strong/45 open:flex open:flex-col"
    >
      <header className="flex shrink-0 items-center gap-2 border-b border-line py-1 pr-2 pl-screen">
        <h2 id={titleId} className="flex-1 py-3 text-lg font-bold">
          {title}
        </h2>
        <button
          type="button"
          onClick={close}
          aria-label="닫기"
          className="flex size-11 shrink-0 items-center justify-center rounded-sm text-muted hover:text-ink focus-visible:outline-2 focus-visible:outline-brand"
        >
          <Icon name="close" size={20} />
        </button>
      </header>
      <div
        ref={bodyRef}
        // 스크롤 영역은 키보드로도 스크롤할 수 있게 포커스를 받을 수 있어야 한다
        tabIndex={0}
        className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-screen py-5 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-brand"
      >
        {children}
      </div>
      {footer && (
        <div className="shrink-0 border-t border-line px-screen pt-3 pb-[calc(env(safe-area-inset-bottom)+12px)]">
          {footer}
        </div>
      )}
    </dialog>
  );
}
