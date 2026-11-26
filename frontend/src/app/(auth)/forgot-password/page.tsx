import type { Metadata } from "next";

import { AuthScreen, FormPlaceholder } from "@/components/auth/AuthScreen";

export const metadata: Metadata = { title: "비밀번호 찾기" };

export default function ForgotPasswordPage() {
  return (
    <AuthScreen title="비밀번호 찾기" description="가입한 이메일을 입력하면 비밀번호를 다시 설정할 수 있는 링크를 보내드려요.">
      <FormPlaceholder />
    </AuthScreen>
  );
}
