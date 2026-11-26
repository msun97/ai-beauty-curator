import { NextResponse, type NextRequest } from "next/server";

import { API_BASE_URL } from "@/lib/api/backend";
import { ACCESS_TOKEN_COOKIE, isAccessTokenAlive, REFRESH_TOKEN_COOKIE } from "@/lib/auth/accessToken";
import { loginPath, safeNextPath } from "@/lib/auth/nextPath";
import { appendSetCookies, clearAuthCookies, requestHeadersWithAccessToken } from "@/lib/auth/proxyCookies";
import { refreshTokens } from "@/lib/auth/refresh";
import { isProtectedPath } from "@/lib/auth/routes";
import { checkSession } from "@/lib/auth/session";

/**
 * 화면을 그리기 전에 로그인 여부로 접근을 나눈다.
 *
 * 1) 보호 화면(마이·저장·추천): 액세스 쿠키가 살아 있으면 그대로 보여준다.
 *    하단 탭 바가 이 화면들을 미리 불러오므로(prefetch) 이때는 백엔드에 묻지 않고 쿠키 만료 시각만 본다.
 *    액세스 쿠키가 없거나 만료됐지만 리프레시 쿠키가 있으면 백엔드에 대신 재발급을 요청해(FIX-004)
 *    성공하면 새 쿠키로 화면을 보여주고, 실패하면 /login?next=원래경로로 보낸다.
 *    탭 바 미리 불러오기로 재발급이 동시에 겹쳐도 백엔드의 유예 시간(FIX-003)으로 모두 성공한다.
 *    쿠키가 살아 보이지만 실제로는 무효인 경우는, 화면의 API 호출이 401을 받아
 *    세션 만료 처리(SessionExpiryHandler)로 로그인 화면에 보내진다.
 * 2) 로그인·가입·비밀번호 찾기: 이미 로그인했으면 홈(또는 ?next=)으로 보낸다.
 *    잘못 보내면 로그인 화면에 못 들어가므로, 쿠키가 살아 보일 때 백엔드에 한 번 확인한다.
 *    액세스 토큰을 쓸 수 없지만(없음·만료·무효) 리프레시 쿠키가 있으면 대신 재발급해 보고(FIX-004),
 *    성공하면 로그인한 사람이므로 새 쿠키와 함께 홈(또는 ?next=)으로 보낸다.
 *    재발급도 거절되면 쿠키를 모두 지우고 화면을 보여준다.
 * - /reset-password는 로그인 중에도 메일 링크로 열 수 있어야 하므로 대상에서 뺀다.
 */
export async function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  const token = request.cookies.get(ACCESS_TOKEN_COOKIE)?.value;

  if (isProtectedPath(pathname)) {
    if (isAccessTokenAlive(token)) {
      return NextResponse.next();
    }
    const toLogin = () => NextResponse.redirect(new URL(loginPath(`${pathname}${search}`), request.url));

    const refreshToken = request.cookies.get(REFRESH_TOKEN_COOKIE)?.value;
    if (!refreshToken) {
      return toLogin();
    }
    const result = await refreshTokens(refreshToken, API_BASE_URL);
    if (result.status === "refreshed") {
      // ② 지금 그리는 화면이 새 액세스 토큰을 읽도록 요청 쿠키를 바꾸고, ① 브라우저에는 새 쿠키를 심는다
      const response = NextResponse.next({
        request: { headers: requestHeadersWithAccessToken(request, result.accessToken) },
      });
      return appendSetCookies(response, result.setCookies);
    }
    // 리프레시 토큰이 만료·폐기됐으면 쿠키를 지운다. 백엔드에 연결할 수 없을 때는 쿠키를 남겨 다음에 다시 시도한다
    return result.status === "invalid" ? clearAuthCookies(toLogin()) : toLogin();
  }

  // 로그인 화면에 돌아갈 곳(?next=)이 있었다면 그곳으로, 없으면 홈으로
  const toNext = () =>
    NextResponse.redirect(new URL(safeNextPath(request.nextUrl.searchParams.get("next")), request.url));

  const status = await checkSession(token, API_BASE_URL);
  if (status === "signed-in") {
    return toNext();
  }

  // 액세스 토큰을 쓸 수 없을 때만 재발급을 시도한다.
  // (토큰은 살아 보이는데 백엔드에 연결할 수 없어 확인하지 못한 경우는 시도하지 않고 화면을 보여준다)
  const accessUnusable = !isAccessTokenAlive(token) || status === "invalid";
  const refreshToken = request.cookies.get(REFRESH_TOKEN_COOKIE)?.value;
  if (accessUnusable && refreshToken) {
    const result = await refreshTokens(refreshToken, API_BASE_URL);
    if (result.status === "refreshed") {
      return appendSetCookies(toNext(), result.setCookies);
    }
    if (result.status === "invalid") {
      return clearAuthCookies(NextResponse.next());
    }
  }

  const response = NextResponse.next();
  if (status === "invalid") {
    // 백엔드가 발급한 쿠키와 같은 경로(/)로 지워야 브라우저에서 사라진다
    response.cookies.delete({ name: ACCESS_TOKEN_COOKIE, path: "/" });
  }
  return response;
}

export const config = {
  // lib/auth/routes.ts의 GUEST_ONLY_PATHS, PROTECTED_PATHS와 같게 유지한다 (빌드 때 상수로만 읽힘)
  matcher: ["/login", "/signup", "/forgot-password", "/my/:path*", "/saved/:path*", "/recommend/:path*"],
};
