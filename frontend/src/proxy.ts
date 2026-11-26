import { NextResponse, type NextRequest } from "next/server";

import { API_BASE_URL } from "@/lib/api/backend";
import { ACCESS_TOKEN_COOKIE } from "@/lib/auth/accessToken";
import { checkSession } from "@/lib/auth/session";

/**
 * 이미 로그인한 사용자가 로그인·회원가입·비밀번호 찾기 화면에 오면 화면을 그리기 전에 홈으로 보낸다.
 *
 * - 쿠키가 없거나 만료됐으면 네트워크 요청 없이 그대로 통과한다.
 * - 쿠키가 살아 보이면 백엔드에 한 번 물어 실제 로그인 상태를 확인한다.
 *   서명 키 교체·탈퇴 등으로 무효(401)면 쿠키를 지우고 화면을 보여준다.
 * - 이 세 화면에서만 실행되므로 백엔드 확인 비용은 작다.
 * - /reset-password는 로그인 중에도 메일 링크로 열 수 있어야 하므로 대상에서 뺀다.
 */
export async function proxy(request: NextRequest) {
  const token = request.cookies.get(ACCESS_TOKEN_COOKIE)?.value;
  const status = await checkSession(token, API_BASE_URL);

  if (status === "signed-in") {
    return NextResponse.redirect(new URL("/", request.url));
  }

  const response = NextResponse.next();
  if (status === "invalid") {
    // 백엔드가 발급한 쿠키와 같은 경로(/)로 지워야 브라우저에서 사라진다
    response.cookies.delete({ name: ACCESS_TOKEN_COOKIE, path: "/" });
  }
  return response;
}

export const config = {
  matcher: ["/login", "/signup", "/forgot-password"],
};
