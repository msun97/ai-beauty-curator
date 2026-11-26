import { describe, expect, it, vi } from "vitest";

import { cookieValueFromSetCookies, refreshTokens } from "./refresh";

const BASE = "http://backend";

/** 백엔드 재발급 성공 응답과 같은 Set-Cookie 3개 (액세스, 리프레시, 예전 경로 리프레시 삭제) */
const SUCCESS_SET_COOKIES = [
  "access_token=new.access.jwt; Path=/; Max-Age=900; HttpOnly; SameSite=Lax",
  "refresh_token=new-refresh; Path=/; Max-Age=1209600; HttpOnly; SameSite=Lax",
  "refresh_token=; Path=/api/auth; Max-Age=0; HttpOnly; SameSite=Lax",
];

function response(status: number, setCookies: string[] = []) {
  const headers = new Headers();
  for (const value of setCookies) headers.append("Set-Cookie", value);
  return new Response(status === 204 ? null : "{}", { status, headers });
}

function fetcherReturning(res: Response) {
  return vi.fn(async () => res) as unknown as typeof fetch & ReturnType<typeof vi.fn>;
}

describe("refreshTokens", () => {
  it("리프레시 쿠키와 CSRF 헤더를 붙여 백엔드 재발급 API를 POST로 부른다", async () => {
    const fetcher = fetcherReturning(response(200, SUCCESS_SET_COOKIES));
    await refreshTokens("old-refresh", BASE, fetcher);

    expect(fetcher).toHaveBeenCalledTimes(1);
    const [url, init] = fetcher.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(`${BASE}/api/auth/refresh`);
    expect(init.method).toBe("POST");
    expect(init.headers).toMatchObject({ Cookie: "refresh_token=old-refresh", "X-Requested-With": "fetch" });
    expect(init.signal).toBeInstanceOf(AbortSignal);
  });

  it("200이면 새 액세스 토큰과 브라우저에 전달할 Set-Cookie 목록을 돌려준다", async () => {
    const result = await refreshTokens("old-refresh", BASE, fetcherReturning(response(200, SUCCESS_SET_COOKIES)));
    expect(result).toEqual({ status: "refreshed", accessToken: "new.access.jwt", setCookies: SUCCESS_SET_COOKIES });
  });

  it("401이면 invalid (리프레시 토큰 만료·폐기)", async () => {
    expect(await refreshTokens("old-refresh", BASE, fetcherReturning(response(401)))).toEqual({ status: "invalid" });
  });

  it("5xx·401 외의 오류(설정 문제 등)는 unavailable — 멀쩡한 쿠키를 지우지 않도록", async () => {
    expect(await refreshTokens("r", BASE, fetcherReturning(response(500)))).toEqual({ status: "unavailable" });
    expect(await refreshTokens("r", BASE, fetcherReturning(response(503)))).toEqual({ status: "unavailable" });
    expect(await refreshTokens("r", BASE, fetcherReturning(response(403)))).toEqual({ status: "unavailable" });
  });

  it("네트워크 오류·시간 초과는 unavailable", async () => {
    const down = vi.fn(async () => {
      throw new TypeError("fetch failed");
    }) as unknown as typeof fetch;
    const timeout = vi.fn(async () => {
      throw new DOMException("The operation timed out.", "TimeoutError");
    }) as unknown as typeof fetch;
    expect(await refreshTokens("r", BASE, down)).toEqual({ status: "unavailable" });
    expect(await refreshTokens("r", BASE, timeout)).toEqual({ status: "unavailable" });
  });

  it("200인데 새 액세스 토큰이 없으면 unavailable (백엔드 약속과 다른 응답)", async () => {
    const onlyRefresh = [SUCCESS_SET_COOKIES[1]];
    expect(await refreshTokens("r", BASE, fetcherReturning(response(200, onlyRefresh)))).toEqual({
      status: "unavailable",
    });
  });
});

describe("cookieValueFromSetCookies", () => {
  it("이름이 같은 쿠키의 값을 꺼내고, 값이 빈 삭제용 쿠키는 건너뛴다", () => {
    expect(cookieValueFromSetCookies(SUCCESS_SET_COOKIES, "access_token")).toBe("new.access.jwt");
    expect(cookieValueFromSetCookies(SUCCESS_SET_COOKIES, "refresh_token")).toBe("new-refresh");
    expect(cookieValueFromSetCookies([SUCCESS_SET_COOKIES[2]], "refresh_token")).toBeUndefined();
  });

  it("이름이 앞부분만 같은 쿠키와 혼동하지 않는다", () => {
    expect(cookieValueFromSetCookies(["access_token_old=x; Path=/"], "access_token")).toBeUndefined();
    expect(cookieValueFromSetCookies([], "access_token")).toBeUndefined();
  });
});
