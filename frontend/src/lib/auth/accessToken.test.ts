import { describe, expect, it } from "vitest";

import { isAccessTokenAlive } from "./accessToken";

const NOW_MS = 1_800_000_000_000;

function token(payload: object): string {
  const encode = (value: object) =>
    btoa(JSON.stringify(value)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  return `${encode({ alg: "HS256" })}.${encode(payload)}.signature`;
}

describe("isAccessTokenAlive", () => {
  it("만료 전 토큰은 살아 있다", () => {
    expect(isAccessTokenAlive(token({ exp: NOW_MS / 1000 + 600 }), NOW_MS)).toBe(true);
  });

  it("만료됐거나 만료 직전(10초 이내) 토큰은 만료로 본다", () => {
    expect(isAccessTokenAlive(token({ exp: NOW_MS / 1000 - 1 }), NOW_MS)).toBe(false);
    expect(isAccessTokenAlive(token({ exp: NOW_MS / 1000 + 5 }), NOW_MS)).toBe(false);
  });

  it("쿠키가 없거나 형식이 깨졌으면 로그인 안 한 것으로 본다", () => {
    expect(isAccessTokenAlive(undefined, NOW_MS)).toBe(false);
    expect(isAccessTokenAlive("", NOW_MS)).toBe(false);
    expect(isAccessTokenAlive("not-a-jwt", NOW_MS)).toBe(false);
    expect(isAccessTokenAlive("a.!!!.c", NOW_MS)).toBe(false);
    expect(isAccessTokenAlive(token({ sub: "1" }), NOW_MS)).toBe(false);
    expect(isAccessTokenAlive(token({ exp: "soon" }), NOW_MS)).toBe(false);
  });
});
