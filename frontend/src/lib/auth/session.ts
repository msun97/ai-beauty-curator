import { isAccessTokenAlive } from "./accessToken";

/**
 * - signed-in: 백엔드가 로그인 상태라고 확인함
 * - invalid:   쿠키는 있었지만 백엔드가 무효라고 함(401). 쿠키를 지워야 함
 * - guest:     쿠키가 없거나 만료됨, 또는 백엔드에 연결할 수 없어 확인하지 못함
 */
export type SessionStatus = "signed-in" | "invalid" | "guest";

const CHECK_TIMEOUT_MS = 2_000;

/**
 * 쿠키가 살아 보일 때만 백엔드에 한 번 물어 실제 로그인 상태를 확인한다.
 * 쿠키가 없거나 만료됐으면 네트워크 요청 없이 바로 guest.
 */
export async function checkSession(
  accessToken: string | undefined,
  apiBaseUrl: string,
  fetcher: typeof fetch = fetch,
  nowMs: number = Date.now(),
): Promise<SessionStatus> {
  if (!accessToken || !isAccessTokenAlive(accessToken, nowMs)) {
    return "guest";
  }
  try {
    const res = await fetcher(`${apiBaseUrl}/api/users/me`, {
      headers: { Authorization: `Bearer ${accessToken}` },
      cache: "no-store",
      signal: AbortSignal.timeout(CHECK_TIMEOUT_MS),
    });
    if (res.ok) {
      return "signed-in";
    }
    return res.status === 401 ? "invalid" : "guest";
  } catch {
    // 백엔드가 꺼졌거나 응답이 늦으면 확인을 포기하고 화면을 그대로 보여준다
    return "guest";
  }
}
