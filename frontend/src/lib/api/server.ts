import "server-only";

import { cookies, headers } from "next/headers";

import { ApiError, TRACE_ID_HEADER, networkError, readBody, toApiError } from "./errors";
import type { AuthUser } from "./types";

/**
 * 서버 컴포넌트·서버 액션용 API 호출 계층 (ADR-004).
 *
 * - 브라우저를 거치지 않고 백엔드(API_BASE_URL)를 직접 호출한다.
 * - 요청 쿠키의 Access Token을 Authorization: Bearer 헤더로 전달한다.
 * - 요청 추적 ID(X-Request-Id)를 넘겨, 백엔드 로그에서 같은 ID로 찾을 수 있게 한다.
 * - 서버 컴포넌트는 쿠키를 바꿀 수 없으므로 토큰이 만료되면 401을 그대로 받는다.
 *   재발급은 브라우저 쪽(client.ts)에서 처리한다.
 */

const API_BASE_URL = process.env.API_BASE_URL ?? "http://localhost:8090";
const ACCESS_TOKEN_COOKIE = "access_token";
const DEFAULT_TIMEOUT_MS = 5_000;
const SAFE_TRACE_ID = /^[A-Za-z0-9-]{1,64}$/;

export type ServerRequestInit = RequestInit & { timeoutMs?: number };

/** 들어온 요청에 추적 ID가 있으면 이어 쓰고, 없으면 새로 만든다. */
async function resolveTraceId(): Promise<string> {
  const incoming = (await headers()).get(TRACE_ID_HEADER);
  return incoming && SAFE_TRACE_ID.test(incoming)
    ? incoming
    : crypto.randomUUID().replaceAll("-", "").slice(0, 16);
}

/** 응답을 그대로 돌려주는 저수준 호출. 네트워크 오류·타임아웃은 ApiError(NETWORK_ERROR)로 던진다. */
export async function serverFetch(path: string, init: ServerRequestInit = {}): Promise<Response> {
  const { timeoutMs = DEFAULT_TIMEOUT_MS, ...requestInit } = init;
  const requestHeaders = new Headers(requestInit.headers);

  const accessToken = (await cookies()).get(ACCESS_TOKEN_COOKIE)?.value;
  if (accessToken) {
    requestHeaders.set("Authorization", `Bearer ${accessToken}`);
  }
  requestHeaders.set(TRACE_ID_HEADER, await resolveTraceId());
  if (requestInit.body && !requestHeaders.has("Content-Type")) {
    requestHeaders.set("Content-Type", "application/json");
  }

  try {
    return await fetch(`${API_BASE_URL}${path}`, {
      ...requestInit,
      headers: requestHeaders,
      cache: "no-store",
      signal: requestInit.signal ?? AbortSignal.timeout(timeoutMs),
    });
  } catch {
    throw networkError();
  }
}

async function request<T>(method: string, path: string, body?: unknown, init?: ServerRequestInit): Promise<T> {
  const res = await serverFetch(path, {
    ...init,
    method,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!res.ok) {
    throw await toApiError(res);
  }
  return readBody<T>(res);
}

/** 성공하면 응답 본문(JSON)을, 실패하면 ApiError를 던진다. */
export const serverApi = {
  get: <T>(path: string, init?: ServerRequestInit) => request<T>("GET", path, undefined, init),
  post: <T>(path: string, body?: unknown, init?: ServerRequestInit) => request<T>("POST", path, body, init),
};

/**
 * 로그인한 사용자. 로그인하지 않았거나 토큰이 만료됐으면 null.
 * 비회원도 볼 수 있는 화면에서 "로그인 여부에 따라 다르게 보여주기"에 쓴다.
 */
export async function getCurrentUser(): Promise<AuthUser | null> {
  try {
    return await serverApi.get<AuthUser>("/api/users/me");
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) {
      return null;
    }
    throw e;
  }
}

export type ApiHealth = "UP" | "DOWN" | "UNREACHABLE";

/** 백엔드 헬스 체크 */
export async function getApiHealth(): Promise<ApiHealth> {
  try {
    const res = await fetch(`${API_BASE_URL}/actuator/health`, {
      cache: "no-store",
      signal: AbortSignal.timeout(3000),
    });
    const body = (await res.json()) as { status?: string };
    return body.status === "UP" ? "UP" : "DOWN";
  } catch {
    return "UNREACHABLE";
  }
}
