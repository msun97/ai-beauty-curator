"use client";

import Link from "next/link";
import { useEffect } from "react";

import { describeError } from "@/lib/api/errors";

import { Button, buttonClassName } from "./Button";
import { ErrorState } from "./ErrorState";

export type RouteErrorProps = { error: unknown; retry: () => void };

/**
 * error.tsx 공용 화면. 렌더링 중 예상하지 못한 오류가 나면 이 화면으로 대신한다.
 * retry()는 해당 구간을 서버에서 다시 받아와 그린다 (Next.js 16).
 */
export function RouteError({ error, retry }: RouteErrorProps) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  const { message, traceId } = describeError(error);

  return (
    <ErrorState
      title="화면을 불러오지 못했어요"
      description={message}
      traceId={traceId}
      actions={
        <>
          <Button onClick={() => retry()}>다시 시도</Button>
          <Link href="/" className={buttonClassName("secondary")}>
            홈으로
          </Link>
        </>
      }
    />
  );
}
