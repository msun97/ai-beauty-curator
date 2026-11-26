import type { ReactNode } from "react";

type AuthScreenProps = {
  title: string;
  description?: string;
  children?: ReactNode;
};

/**
 * 인증 화면의 제목·설명 틀. 입력 폼은 children으로 넣는다.
 * 로그인한 사용자를 홈으로 보내는 일은 src/proxy.ts가 화면을 그리기 전에 처리한다.
 */
export function AuthScreen({ title, description, children }: AuthScreenProps) {
  return (
    <main className="flex flex-1 flex-col px-screen pt-6 pb-10">
      <h1 className="text-2xl font-bold">{title}</h1>
      {description && <p className="mt-2 text-sm text-muted">{description}</p>}
      <div className="mt-8 flex flex-1 flex-col">{children}</div>
    </main>
  );
}
