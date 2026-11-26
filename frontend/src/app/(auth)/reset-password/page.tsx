import type { Metadata } from "next";

import { AuthScreen, FormPlaceholder } from "@/components/auth/AuthScreen";

export const metadata: Metadata = {
  title: "새 비밀번호 설정",
  // 메일 링크 주소(token 포함)가 검색 결과나 다른 사이트로 새지 않게 한다
  robots: { index: false },
  referrer: "no-referrer",
};

/**
 * 메일의 재설정 링크(/reset-password?token=…)가 여는 화면.
 * 다른 기기에서 로그인한 채로 링크를 열 수도 있으므로 로그인 상태여도 홈으로 보내지 않는다(proxy 대상 아님).
 * token은 폼을 만드는 단계에서 읽는다.
 */
export default function ResetPasswordPage() {
  return (
    <AuthScreen title="새 비밀번호 설정" description="새로 사용할 비밀번호를 입력해 주세요.">
      <FormPlaceholder />
    </AuthScreen>
  );
}
