/**
 * 로그인 여부에 따라 접근을 나누는 화면 목록. proxy와 화면 코드가 함께 쓴다.
 * proxy.ts의 config.matcher는 빌드 때 상수로만 읽히므로, 경로를 바꾸면 그쪽도 같이 바꾼다.
 */

/** 로그인한 사용자는 들어올 필요가 없는 화면 (들어오면 홈 또는 ?next=로 보낸다) */
export const GUEST_ONLY_PATHS = ["/login", "/signup", "/forgot-password"] as const;

/** 로그인해야 볼 수 있는 화면 (FR-AUTH-03: 마이, 저장, 추천) */
export const PROTECTED_PATHS = ["/my", "/saved", "/recommend"] as const;

function matches(pathname: string, paths: readonly string[]): boolean {
  return paths.some((path) => pathname === path || pathname.startsWith(`${path}/`));
}

export function isGuestOnlyPath(pathname: string): boolean {
  return matches(pathname, GUEST_ONLY_PATHS);
}

export function isProtectedPath(pathname: string): boolean {
  return matches(pathname, PROTECTED_PATHS);
}
