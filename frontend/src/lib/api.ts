import { cookies } from "next/headers";

const API_BASE_URL = process.env.API_BASE_URL ?? "http://localhost:8090";

export type ApiHealth = "UP" | "DOWN" | "UNREACHABLE";

/** 백엔드 헬스 체크. 서버 컴포넌트에서만 호출한다. */
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

/**
 * 서버 컴포넌트에서 로그인 사용자 기준으로 백엔드를 호출한다.
 * 요청 쿠키의 Access Token을 Authorization 헤더로 전달한다 (ADR-004).
 * 서버 컴포넌트는 쿠키를 갱신할 수 없으므로, 토큰이 만료되면 401을 그대로 돌려주고
 * 재발급은 브라우저 쪽(client-api.ts)에서 처리한다.
 */
export async function serverFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const accessToken = (await cookies()).get("access_token")?.value;
  const headers = new Headers(init.headers);
  if (accessToken) {
    headers.set("Authorization", `Bearer ${accessToken}`);
  }
  return fetch(`${API_BASE_URL}${path}`, { ...init, headers, cache: "no-store" });
}
