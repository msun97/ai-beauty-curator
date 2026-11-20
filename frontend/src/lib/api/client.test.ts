import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// Next.js 밖(테스트)에서는 client-only가 그대로 해석되므로 빈 모듈로 대체한다
vi.mock("client-only", () => ({}));

import { ApiError, CSRF_HEADER, apiFetch, clientApi, onSessionExpired } from "./client";

type Call = { url: string; init: RequestInit };

function json(status: number, body: unknown, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...headers },
  });
}

describe("client API 계층", () => {
  let calls: Call[];
  let responder: (url: string, init: RequestInit) => Response | Promise<Response>;

  beforeEach(() => {
    calls = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init: RequestInit) => {
        calls.push({ url, init });
        return responder(url, init);
      }),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("모든 요청에 CSRF 헤더와 same-origin 쿠키 전송을 설정한다", async () => {
    responder = () => json(200, { ok: true });

    await clientApi.get("/api/users/me");

    const headers = new Headers(calls[0].init.headers);
    expect(headers.get(CSRF_HEADER)).toBe("fetch");
    expect(calls[0].init.credentials).toBe("same-origin");
  });

  it("본문이 있으면 JSON으로 보내고 Content-Type을 붙인다", async () => {
    responder = () => json(200, {});

    await clientApi.post("/api/auth/login", { email: "a@b.com", password: "x" });

    expect(calls[0].init.method).toBe("POST");
    expect(calls[0].init.body).toBe('{"email":"a@b.com","password":"x"}');
    expect(new Headers(calls[0].init.headers).get("Content-Type")).toBe("application/json");
  });

  it("401이면 토큰을 재발급하고 원래 요청을 다시 보낸다", async () => {
    let meCalls = 0;
    responder = (url) => {
      if (url === "/api/auth/refresh") return new Response(null, { status: 200 });
      meCalls++;
      return meCalls === 1 ? json(401, { code: "UNAUTHORIZED", message: "" }) : json(200, { id: 1 });
    };

    const user = await clientApi.get<{ id: number }>("/api/users/me");

    expect(user).toEqual({ id: 1 });
    expect(calls.map((c) => c.url)).toEqual(["/api/users/me", "/api/auth/refresh", "/api/users/me"]);
  });

  it("동시에 여러 요청이 401을 받아도 재발급 요청은 한 번만 보낸다", async () => {
    const attempts = new Map<string, number>();
    let releaseRefresh!: () => void;
    const refreshGate = new Promise<void>((resolve) => (releaseRefresh = resolve));

    responder = async (url) => {
      if (url === "/api/auth/refresh") {
        await refreshGate;
        return new Response(null, { status: 200 });
      }
      const n = (attempts.get(url) ?? 0) + 1;
      attempts.set(url, n);
      return n === 1 ? json(401, { code: "UNAUTHORIZED", message: "" }) : json(200, {});
    };

    const pending = Promise.all([clientApi.get("/api/a"), clientApi.get("/api/b"), clientApi.get("/api/c")]);
    await vi.waitFor(() => expect(attempts.size).toBe(3));
    releaseRefresh();
    await pending;

    expect(calls.filter((c) => c.url === "/api/auth/refresh")).toHaveLength(1);
  });

  it("재발급도 실패하면 세션 만료를 알리고 ApiError(401)를 던진다", async () => {
    responder = (url) =>
      url === "/api/auth/refresh"
        ? json(401, { code: "INVALID_REFRESH_TOKEN", message: "로그인이 만료됐어요." })
        : json(401, { code: "UNAUTHORIZED", message: "로그인이 필요해요." });
    const expired = vi.fn();
    const unsubscribe = onSessionExpired(expired);

    await expect(clientApi.get("/api/users/me")).rejects.toMatchObject({ status: 401, code: "UNAUTHORIZED" });
    expect(expired).toHaveBeenCalledOnce();
    unsubscribe();
  });

  it("인증 API(/api/auth/**)의 401은 재발급을 시도하지 않는다", async () => {
    responder = () => json(401, { code: "INVALID_CREDENTIALS", message: "이메일 또는 비밀번호가 올바르지 않아요." });

    await expect(clientApi.post("/api/auth/login", {})).rejects.toBeInstanceOf(ApiError);
    expect(calls).toHaveLength(1);
  });

  it("오류 응답을 코드·필드 오류·추적 ID가 담긴 ApiError로 바꾼다", async () => {
    responder = () =>
      json(400, {
        code: "INVALID_INPUT",
        message: "입력값을 확인해 주세요.",
        fieldErrors: [{ field: "password", message: "비밀번호는 영문과 숫자를 포함해 8자 이상이어야 해요." }],
        traceId: "abc123",
      });

    const error = await clientApi.post("/api/auth/signup", {}).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.code).toBe("INVALID_INPUT");
    expect(apiError.traceId).toBe("abc123");
    expect(apiError.fieldMessage("password")).toContain("8자 이상");
  });

  it("공통 형식이 아닌 오류 응답은 기본 문구로 바꾸고 헤더의 추적 ID를 쓴다", async () => {
    responder = () => new Response("<html>Bad Gateway</html>", { status: 502, headers: { "X-Request-Id": "gw-1" } });

    await expect(clientApi.get("/api/products")).rejects.toMatchObject({
      status: 502,
      code: "INTERNAL_ERROR",
      traceId: "gw-1",
    });
  });

  it("네트워크 오류는 NETWORK_ERROR로 던진다", async () => {
    responder = () => {
      throw new TypeError("Failed to fetch");
    };

    await expect(apiFetch("/api/products")).rejects.toMatchObject({ status: 0, code: "NETWORK_ERROR" });
  });

  it("204 응답은 undefined를 반환한다", async () => {
    responder = () => new Response(null, { status: 204 });

    await expect(clientApi.post("/api/auth/logout")).resolves.toBeUndefined();
  });
});
