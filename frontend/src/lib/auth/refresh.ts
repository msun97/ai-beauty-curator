import { ACCESS_TOKEN_COOKIE, REFRESH_TOKEN_COOKIE } from "./accessToken";

/**
 * proxy가 Refresh Token으로 대신 토큰을 재발급한다 (FIX-004).
 *
 * 액세스 토큰(15분)이 만료된 뒤 화면을 이동하면 브라우저 요청에는 리프레시 쿠키만 남는다.
 * 이때 Next.js 서버가 백엔드를 직접 불러 새 토큰을 받아, 사용자가 로그인 화면으로 튕기지 않게 한다.
 *
 * - refreshed:   새 액세스 토큰과, 브라우저에 그대로 전달할 백엔드의 Set-Cookie 목록
 * - invalid:     401. 리프레시 토큰이 만료·폐기됨 → 쿠키를 지우고 비회원으로 처리
 * - unavailable: 네트워크 오류·시간 초과·5xx 등 → 판단을 미루고 쿠키는 그대로 둔다
 */
export type RefreshResult =
  | { status: "refreshed"; accessToken: string; setCookies: string[] }
  | { status: "invalid" }
  | { status: "unavailable" };

const REFRESH_TIMEOUT_MS = 2_000;
/** 백엔드 CsrfHeaderFilter가 상태를 바꾸는 요청에 요구하는 헤더 */
const CSRF_HEADER = "X-Requested-With";

/** Set-Cookie 목록에서 이름이 name인 쿠키의 값을 꺼낸다 (값이 비어 있으면 삭제용 쿠키이므로 무시) */
export function cookieValueFromSetCookies(setCookies: string[], name: string): string | undefined {
  for (const header of setCookies) {
    const pair = header.split(";", 1)[0];
    const eq = pair.indexOf("=");
    if (eq > 0 && pair.slice(0, eq).trim() === name) {
      const value = pair.slice(eq + 1).trim();
      if (value) return value;
    }
  }
  return undefined;
}

export async function refreshTokens(
  refreshToken: string,
  apiBaseUrl: string,
  fetcher: typeof fetch = fetch,
): Promise<RefreshResult> {
  let res: Response;
  try {
    res = await fetcher(`${apiBaseUrl}/api/auth/refresh`, {
      method: "POST",
      headers: {
        Cookie: `${REFRESH_TOKEN_COOKIE}=${refreshToken}`,
        [CSRF_HEADER]: "fetch",
      },
      cache: "no-store",
      signal: AbortSignal.timeout(REFRESH_TIMEOUT_MS),
    });
  } catch {
    return { status: "unavailable" };
  }

  if (res.status === 401) {
    return { status: "invalid" };
  }
  if (!res.ok) {
    return { status: "unavailable" };
  }

  const setCookies = res.headers.getSetCookie();
  const accessToken = cookieValueFromSetCookies(setCookies, ACCESS_TOKEN_COOKIE);
  // 성공 응답인데 새 액세스 토큰이 없으면 백엔드 계약과 다른 상황이므로 판단을 미룬다
  if (!accessToken) {
    return { status: "unavailable" };
  }
  return { status: "refreshed", accessToken, setCookies };
}
