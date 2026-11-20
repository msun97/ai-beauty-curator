import type { ReactNode } from "react";

import { Icon, type IconName } from "./Icon";
import { TraceId } from "./TraceId";

type ErrorStateProps = {
  icon?: IconName;
  title: string;
  description: string;
  traceId?: string | null;
  /** 다시 시도, 홈으로 등 버튼 영역 */
  actions?: ReactNode;
};

/** 화면 전체를 대신하는 오류 안내 (error.tsx, not-found.tsx 공용) */
export function ErrorState({ icon = "alert", title, description, traceId, actions }: ErrorStateProps) {
  return (
    <main className="flex flex-1 flex-col items-center justify-center px-screen py-16 text-center">
      <div className="flex size-16 items-center justify-center rounded-full bg-brand-light text-brand">
        <Icon name={icon} size={30} />
      </div>
      <h1 className="mt-5 text-xl font-bold">{title}</h1>
      <p className="mt-2 text-sm whitespace-pre-line text-muted">{description}</p>
      <TraceId value={traceId} className="mt-3" />
      {actions && <div className="mt-8 flex w-full max-w-xs flex-col gap-2.5">{actions}</div>}
    </main>
  );
}
