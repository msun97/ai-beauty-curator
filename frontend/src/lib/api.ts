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
