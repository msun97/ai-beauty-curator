import { clientApi } from "@/lib/api/client";
import type { AuthUser } from "@/lib/api/types";

import type { toSignupRequest } from "./signupValidation";

/**
 * 이메일 인증 코드 가입 API (FR-AUTH-01, 수정 요청 FIX-008의 API 계약).
 * 백엔드 구현 전이라도 이 계약대로 화면을 먼저 만든다.
 *
 * 1) 코드 요청: 가입 여부와 관계없이 항상 같은 응답(202) → 가입 여부가 드러나지 않음
 * 2) 코드 확인: 코드를 맞힌 사람(= 메일함 주인)에게만 가입 여부를 알려준다
 * 3) 가입: 2)에서 받은 1회용 토큰으로 가입하고 로그인 쿠키를 받는다
 */

export type VerificationResult = { registered: true } | { registered: false; verificationToken: string };

export const signupApi = {
  requestCode: (email: string) => clientApi.post<void>("/api/auth/email-verification/request", { email }),

  confirmCode: (email: string, code: string) =>
    clientApi.post<VerificationResult>("/api/auth/email-verification/confirm", { email, code }),

  signup: (body: ReturnType<typeof toSignupRequest>) => clientApi.post<AuthUser>("/api/auth/signup", body),
};
