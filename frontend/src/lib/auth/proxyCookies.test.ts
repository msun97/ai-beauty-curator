import { NextRequest, NextResponse } from "next/server";
import { describe, expect, it } from "vitest";

import { appendSetCookies, clearAuthCookies, requestHeadersWithAccessToken } from "./proxyCookies";

function request(cookie?: string) {
  return new NextRequest("http://localhost:3100/my", { headers: cookie ? { cookie } : {} });
}

describe("requestHeadersWithAccessToken", () => {
  it("액세스 토큰이 없던 요청에 새 토큰을 추가하고, 다른 쿠키는 그대로 둔다", () => {
    const headers = requestHeadersWithAccessToken(request("refresh_token=r1; theme=dark"), "new.jwt");
    expect(headers.get("cookie")).toBe("refresh_token=r1; theme=dark; access_token=new.jwt");
  });

  it("만료된 액세스 토큰은 새 값으로 바꾼다 (같은 이름이 두 번 들어가지 않게)", () => {
    const headers = requestHeadersWithAccessToken(request("access_token=old.jwt; refresh_token=r1"), "new.jwt");
    expect(headers.get("cookie")).toBe("refresh_token=r1; access_token=new.jwt");
  });

  it("쿠키 외의 요청 헤더는 유지한다", () => {
    const req = new NextRequest("http://localhost:3100/my", { headers: { cookie: "refresh_token=r1", "x-request-id": "t1" } });
    expect(requestHeadersWithAccessToken(req, "new.jwt").get("x-request-id")).toBe("t1");
  });
});

describe("appendSetCookies", () => {
  it("백엔드가 준 Set-Cookie를 모두 그대로 붙인다", () => {
    const setCookies = [
      "access_token=new.jwt; Path=/; Max-Age=900; HttpOnly; SameSite=Lax",
      "refresh_token=r2; Path=/; Max-Age=1209600; HttpOnly; SameSite=Lax",
      "refresh_token=; Path=/api/auth; Max-Age=0; HttpOnly; SameSite=Lax",
    ];
    const response = appendSetCookies(NextResponse.next(), setCookies);
    expect(response.headers.getSetCookie()).toEqual(setCookies);
  });

  it("리다이렉트 응답에도 붙는다 (로그인 화면 → 홈으로 보낼 때)", () => {
    const response = appendSetCookies(NextResponse.redirect("http://localhost:3100/"), ["access_token=new.jwt; Path=/"]);
    expect(response.headers.getSetCookie()).toContain("access_token=new.jwt; Path=/");
  });
});

describe("clearAuthCookies", () => {
  it("액세스·리프레시 쿠키를 경로 /로 지우고, 예전 경로(/api/auth) 리프레시 쿠키도 지운다", () => {
    const setCookies = clearAuthCookies(NextResponse.next()).headers.getSetCookie();
    const deleted = (name: string, path: string) =>
      setCookies.some((c) => c.startsWith(`${name}=;`) && c.includes(`Path=${path}`) && /Max-Age=0|Expires=Thu, 01 Jan 1970/.test(c));
    expect(deleted("access_token", "/")).toBe(true);
    expect(deleted("refresh_token", "/")).toBe(true);
    expect(deleted("refresh_token", "/api/auth")).toBe(true);
  });
});
