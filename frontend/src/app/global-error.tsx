"use client"; // 오류 화면(Error Boundary)은 클라이언트 컴포넌트여야 한다

import { RouteError, type RouteErrorProps } from "@/components/ui/RouteError";

import "./globals.css";

/**
 * 루트 레이아웃 자체에서 난 오류. 루트 레이아웃을 대신하므로 html·body를 직접 그린다.
 * 루트 레이아웃의 글꼴 변수가 없으므로 시스템 글꼴을 쓴다.
 */
export default function GlobalError(props: RouteErrorProps) {
  return (
    <html lang="ko">
      <body className="flex min-h-dvh flex-col font-[system-ui,sans-serif]">
        <title>일시적인 오류 | pickly</title>
        <div className="mx-auto flex w-full max-w-app flex-1 flex-col">
          <RouteError {...props} />
        </div>
      </body>
    </html>
  );
}
