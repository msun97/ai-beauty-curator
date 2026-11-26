import { describe, expect, it, vi } from "vitest";

import { checkSession } from "./session";

const NOW_MS = 1_800_000_000_000;
const BASE = "http://backend";

function token(exp: number): string {
  const encode = (value: object) =>
    btoa(JSON.stringify(value)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  return `${encode({ alg: "HS256" })}.${encode({ sub: "1", exp })}.signature`;
}

const alive = token(NOW_MS / 1000 + 600);

function respond(status: number) {
  return vi.fn(async () => new Response(null, { status })) as unknown as typeof fetch;
}

describe("checkSession", () => {
  it("쿠키가 없거나 만료됐으면 백엔드에 묻지 않고 guest", async () => {
    const fetcher = respond(200);
    expect(await checkSession(undefined, BASE, fetcher, NOW_MS)).toBe("guest");
    expect(await checkSession(token(NOW_MS / 1000 - 60), BASE, fetcher, NOW_MS)).toBe("guest");
    expect(fetcher).not.toHaveBeenCalled();
  });

  it("백엔드가 200이면 signed-in, 토큰을 Bearer로 전달한다", async () => {
    const fetcher = respond(200);
    expect(await checkSession(alive, BASE, fetcher, NOW_MS)).toBe("signed-in");
    expect(fetcher).toHaveBeenCalledWith(
      `${BASE}/api/users/me`,
      expect.objectContaining({ headers: { Authorization: `Bearer ${alive}` } }),
    );
  });

  it("백엔드가 401이면 invalid (서명 키 교체, 탈퇴 등)", async () => {
    expect(await checkSession(alive, BASE, respond(401), NOW_MS)).toBe("invalid");
  });

  it("백엔드 오류·연결 실패는 확인하지 못한 것으로 보고 guest", async () => {
    expect(await checkSession(alive, BASE, respond(500), NOW_MS)).toBe("guest");
    const down = vi.fn(async () => {
      throw new TypeError("fetch failed");
    }) as unknown as typeof fetch;
    expect(await checkSession(alive, BASE, down, NOW_MS)).toBe("guest");
  });
});
