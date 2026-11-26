import { NextResponse, type NextRequest } from "next/server";

import { API_BASE_URL } from "@/lib/api/backend";
import { ACCESS_TOKEN_COOKIE, isAccessTokenAlive } from "@/lib/auth/accessToken";
import { loginPath, safeNextPath } from "@/lib/auth/nextPath";
import { isProtectedPath } from "@/lib/auth/routes";
import { checkSession } from "@/lib/auth/session";

/**
 * 화면을 그리기 전에 로그인 여부로 접근을 나눈다.
 *
 * 1) 보호 화면(마이·저장·추천): 로그인 쿠키가 없거나 만료됐으면 /login?next=원래경로로 보낸다.
 *    하단 탭 바가 이 화면들을 미리 불러오므로(prefetch) 백엔드에 묻지 않고 쿠키 만료 시각만 본다.
 *    쿠키가 살아 보이지만 실제로는 무효인 경우는, 화면의 API 호출이 401을 받아
 *    세션 만료 처리(SessionExpiryHandler)로 로그인 화면에 보내진다.
 * 2) 로그인·가입·비밀번호 찾기: 이미 로그인했으면 홈(또는 ?next=)으로 보낸다.
 *    잘못 보내면 로그인 화면에 못 들어가므로, 쿠키가 살아 보일 때 백엔드에 한 번 확인한다.
 *    무효(401)면 쿠키를 지우고 화면을 보여준다.
 * - /reset-password는 로그인 중에도 메일 링크로 열 수 있어야 하므로 대상에서 뺀다.
 */
export async function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  const token = request.cookies.get(ACCESS_TOKEN_COOKIE)?.value;

  if (isProtectedPath(pathname)) {
    if (isAccessTokenAlive(token)) {
      return NextResponse.next();
    }
    return NextResponse.redirect(new URL(loginPath(`${pathname}${search}`), request.url));
  }

  const status = await checkSession(token, API_BASE_URL);
  if (status === "signed-in") {
    // 로그인 화면에 돌아갈 곳(?next=)이 있었다면 그곳으로, 없으면 홈으로
    const next = safeNextPath(request.nextUrl.searchParams.get("next"));
    return NextResponse.redirect(new URL(next, request.url));
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
