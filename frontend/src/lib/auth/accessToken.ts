/**
 * proxy에서 쓰는 "낙관적" 로그인 판단.
 *
 * 서명은 검증하지 않고 쿠키에 든 JWT의 만료 시각(exp)만 읽는다.
 * 화면을 어디로 보낼지 정하는 용도일 뿐이고, 실제 데이터 요청은 백엔드가 토큰을 다시 검증한다.
 * (Next.js 인증 가이드: proxy는 모든 요청에서 실행되므로 쿠키만 보고 판단하고 DB·API 조회는 하지 않는다)
 */

export const ACCESS_TOKEN_COOKIE = "access_token";

/**
 * Refresh Token 쿠키 (14일). 백엔드가 모든 경로(/)로 보내므로 proxy도 읽을 수 있다 (FIX-002).
 * 값은 DB에 저장된 무작위 문자열이라 프론트에서 해석하지 않고, 재발급 요청에 그대로 실어 보내기만 한다.
 */
export const REFRESH_TOKEN_COOKIE = "refresh_token";

/** 서버와 브라우저 시계 차이를 감안해 만료 직전 토큰은 만료로 본다 */
const CLOCK_SKEW_SECONDS = 10;

function decodePayload(token: string): unknown {
  const parts = token.split(".");
  if (parts.length !== 3) {
    return null;
  }
  try {
    const base64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), "=");
    return JSON.parse(atob(padded));
  } catch {
    return null;
  }
}

/** 토큰 형식이 맞고 만료 전이면 true. 판단할 수 없으면 false(로그인 안 한 것으로 본다). */
export function isAccessTokenAlive(token: string | undefined, nowMs: number = Date.now()): boolean {
  if (!token) {
    return false;
  }
  const payload = decodePayload(token);
  if (typeof payload !== "object" || payload === null) {
    return false;
  }
  const exp = (payload as { exp?: unknown }).exp;
  return typeof exp === "number" && exp - CLOCK_SKEW_SECONDS > nowMs / 1000;
}
