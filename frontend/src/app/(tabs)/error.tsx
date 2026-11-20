"use client"; // 오류 화면(Error Boundary)은 클라이언트 컴포넌트여야 한다

import { RouteError, type RouteErrorProps } from "@/components/ui/RouteError";

/**
 * 탭 화면에서 난 오류. 이 파일은 (tabs)/layout.tsx 안쪽만 감싸므로
 * 오류 화면이 떠도 하단 탭 바는 그대로 남아 다른 탭으로 이동할 수 있다.
 */
export default function TabsError(props: RouteErrorProps) {
  return <RouteError {...props} />;
}
