import type { ReactNode } from "react";

import { BottomTabBar } from "@/components/layout/BottomTabBar";

/**
 * 하단 탭 바가 있는 화면(홈·검색·추천·저장·마이)의 공통 레이아웃.
 * 로그인·상품 상세처럼 탭 바가 없는 화면은 이 그룹 밖에 만든다.
 *
 * - 모바일 앱 너비(max-w-app)로 가운데 정렬해 데스크톱에서도 같은 화면을 보여준다.
 * - 본문 아래에 탭 바 높이 + 아이폰 홈 인디케이터 영역만큼 여백을 둬 내용이 가려지지 않게 한다.
 */
export default function TabsLayout({ children }: { children: ReactNode }) {
  return (
    <div className="mx-auto flex min-h-dvh w-full max-w-app flex-1 flex-col bg-surface">
      <div className="flex flex-1 flex-col pb-[calc(var(--spacing-tab-bar)+env(safe-area-inset-bottom))]">
        {children}
      </div>
      <BottomTabBar />
    </div>
  );
}
