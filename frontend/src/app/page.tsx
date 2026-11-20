import { Suspense } from "react";
import { connection } from "next/server";
import { Logo } from "@/components/Logo";
import { getApiHealth, type ApiHealth } from "@/lib/api/server";

const STATUS_LABEL: Record<ApiHealth, string> = {
  UP: "정상 연결",
  DOWN: "응답 이상",
  UNREACHABLE: "연결 안 됨",
};

async function ApiStatus() {
  await connection(); // 요청 시점에 백엔드 상태를 확인한다
  const health = await getApiHealth();
  return (
    <strong className={health === "UP" ? "text-success" : "text-danger"}>
      {STATUS_LABEL[health]}
    </strong>
  );
}

export default function Home() {
  return (
    <main className="mx-auto flex w-full max-w-app flex-1 flex-col justify-center gap-6 px-screen py-16">
      <Logo className="text-3xl" />
      <p className="font-display text-2xs font-bold tracking-widest text-brand">BEAUTY TREND, FOR YOU</p>
      <h1 className="text-2xl font-bold">
        요즘 뜨는 그 제품,
        <br />
        <span className="text-brand">내 피부에도 맞을까?</span>
      </h1>
      <div className="rounded-card bg-brand-light px-5 py-4 text-sm shadow-soft">
        <span className="text-muted">백엔드 API 상태: </span>
        <Suspense fallback={<span className="text-muted">확인 중…</span>}>
          <ApiStatus />
        </Suspense>
      </div>
    </main>
  );
}
