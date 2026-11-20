import type { IconName } from "@/components/ui/Icon";

export type Tab = { href: string; label: string; icon: IconName };

/** 하단 탭 5개 (기획서 2.2, Figma 공통 컴포넌트) */
export const TABS: readonly Tab[] = [
  { href: "/", label: "홈", icon: "home" },
  { href: "/search", label: "검색", icon: "search" },
  { href: "/recommend", label: "추천", icon: "sparkle" },
  { href: "/saved", label: "저장", icon: "bookmark" },
  { href: "/my", label: "마이", icon: "user" },
];

/** 하위 경로(/search/results, /my/profile 등)에서도 해당 탭을 활성으로 표시한다. 홈은 정확히 "/"일 때만. */
export function isActiveTab(pathname: string, href: string): boolean {
  if (href === "/") return pathname === "/";
  return pathname === href || pathname.startsWith(`${href}/`);
}
