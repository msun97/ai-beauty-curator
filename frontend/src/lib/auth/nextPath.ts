/**
 * 로그인 후 돌아갈 주소(?next=)를 안전한 내부 경로로만 허용한다.
 *
 * 외부 사이트로 보내는 악용(open redirect)을 막는다.
 * 예: /login?next=https://evil.example 또는 //evil.example 로 로그인 직후 가짜 사이트로 보내는 것
 * 허용하지 않는 값이면 홈(/)으로 보낸다.
 */

export const DEFAULT_NEXT_PATH = "/";

/** 로그인 후 다시 보내면 안 되는 화면 (로그인 화면끼리 맴도는 것 방지) */
const AUTH_PATHS = ["/login", "/signup", "/forgot-password", "/reset-password"];

const ORIGIN = "http://internal.invalid";

export function safeNextPath(raw: string | null | undefined): string {
  if (!raw || !raw.startsWith("/") || raw.startsWith("//") || raw.startsWith("/\\")) {
    return DEFAULT_NEXT_PATH;
  }
  // 제어 문자(줄바꿈·탭 등)는 브라우저가 지우면서 //evil 같은 주소로 바뀔 수 있어 거른다
  if (/[\u0000-\u001f\u007f]/.test(raw)) {
    return DEFAULT_NEXT_PATH;
  }

  let url: URL;
  try {
    url = new URL(raw, ORIGIN);
  } catch {
    return DEFAULT_NEXT_PATH;
  }
  if (url.origin !== ORIGIN) {
    return DEFAULT_NEXT_PATH;
  }
  if (AUTH_PATHS.some((path) => url.pathname === path || url.pathname.startsWith(`${path}/`))) {
    return DEFAULT_NEXT_PATH;
  }
  return `${url.pathname}${url.search}${url.hash}`;
}

/** 로그인 화면 주소. 보호된 화면에서 로그인으로 보낼 때 돌아올 곳을 함께 담는다. */
export function loginPath(next?: string): string {
  const safe = safeNextPath(next);
  return safe === DEFAULT_NEXT_PATH ? "/login" : `/login?next=${encodeURIComponent(safe)}`;
}
