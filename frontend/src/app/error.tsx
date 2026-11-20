"use client"; // 오류 화면(Error Boundary)은 클라이언트 컴포넌트여야 한다

import { RouteError, type RouteErrorProps } from "@/components/ui/RouteError";

/** 탭 바 밖의 화면(로그인, 상품 상세 등)에서 난 오류 */
export default function Error(props: RouteErrorProps) {
  return <RouteError {...props} />;
}
