import type { NextRequest, NextResponse } from "next/server";

import { ACCESS_TOKEN_COOKIE, REFRESH_TOKEN_COOKIE } from "./accessToken";

/**
 * proxy가 토큰을 대신 재발급했을 때(FIX-004) 새 쿠키를 두 곳에 넣는 도우미.
 *
 * ① 브라우저: 백엔드가 준 Set-Cookie를 응답에 그대로 붙여, 다음 요청부터 새 쿠키를 쓰게 한다.
 * ② 지금 그리는 화면: 브라우저가 보낸 요청에는 새 토큰이 없으므로, 서버 컴포넌트가 cookies()로
 *    새 액세스 토큰을 읽을 수 있게 요청의 Cookie 헤더를 바꿔서 넘긴다.
 *    (NextResponse.next({ request: { headers } }) — Next.js proxy 문서의 "요청 헤더 바꾸기")
 *    이걸 빠뜨리면 재발급은 됐는데 이번 화면만 옛 쿠키로 그려져 로그인 화면으로 튕긴다.
 */

/** ② 요청의 Cookie 헤더에서 액세스 토큰만 새 값으로 바꾼(없으면 추가한) 요청 헤더 */
export function requestHeadersWithAccessToken(request: NextRequest, accessToken: string): Headers {
  const headers = new Headers(request.headers);
  const cookies = request.cookies
    .getAll()
    .filter((cookie) => cookie.name !== ACCESS_TOKEN_COOKIE)
    .map((cookie) => `${cookie.name}=${cookie.value}`);
  cookies.push(`${ACCESS_TOKEN_COOKIE}=${accessToken}`);
  headers.set("cookie", cookies.join("; "));
  return headers;
}

/** ① 백엔드가 준 Set-Cookie(새 액세스·리프레시 토큰, 예전 경로 쿠키 삭제)를 응답에 그대로 붙인다 */
export function appendSetCookies<T extends NextResponse>(response: T, setCookies: readonly string[]): T {
  for (const value of setCookies) {
    response.headers.append("Set-Cookie", value);
  }
  return response;
}

/**
 * 재발급이 거절됐을 때(리프레시 토큰 만료·폐기) 로그인 쿠키를 모두 지운다.
 * 백엔드가 발급한 경로와 같아야 지워지므로 경로를 맞춘다. 예전 경로(/api/auth)의 리프레시 쿠키도 함께 지운다.
 */
export function clearAuthCookies<T extends NextResponse>(response: T): T {
  response.cookies.delete({ name: ACCESS_TOKEN_COOKIE, path: "/" });
  response.cookies.delete({ name: REFRESH_TOKEN_COOKIE, path: "/" });
  // cookies.delete는 같은 이름을 한 번만 담으므로, 예전 경로 쿠키 삭제는 헤더로 직접 붙인다
  response.headers.append(
    "Set-Cookie",
    `${REFRESH_TOKEN_COOKIE}=; Path=/api/auth; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT`,
  );
  return response;
}
