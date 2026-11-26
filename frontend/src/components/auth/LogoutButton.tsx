"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import { Button } from "@/components/ui/Button";
import { clientApi } from "@/lib/api/client";
import { toast } from "@/lib/toast";

/**
 * 로그아웃: 백엔드가 Refresh Token을 폐기하고 로그인 쿠키를 지운다.
 * 요청이 실패해도(네트워크 오류 등) 화면은 비회원 상태로 돌려보낸다. 쿠키가 남았다면 다음 요청에서 다시 처리된다.
 */
export function LogoutButton() {
  const router = useRouter();
  const [pending, setPending] = useState(false);

  async function logout() {
    if (pending) return;
    setPending(true);
    try {
      await clientApi.post<void>("/api/auth/logout");
      toast.success("로그아웃했어요.");
    } catch (e) {
      toast.error(e);
    }
    router.replace("/");
    router.refresh();
  }

  return (
    <Button variant="secondary" loading={pending} onClick={logout} className="w-full">
      로그아웃
    </Button>
  );
}
