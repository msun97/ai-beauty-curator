import { Suspense } from "react";
import { connection } from "next/server";
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
    <strong className={health === "UP" ? "text-emerald-700" : "text-rose-700"}>
      {STATUS_LABEL[health]}
    </strong>
  );
}

export default function Home() {
  return (
    <main className="mx-auto flex w-full max-w-md flex-1 flex-col justify-center gap-6 px-6 py-16">
      <p className="text-sm font-semibold tracking-widest text-brand">AI BEAUTY CURATOR</p>
      <h1 className="text-2xl font-bold leading-snug">
        요즘 뜨는 그 제품,
        <br />내 피부에도 맞을까?
      </h1>
      <div className="rounded-2xl bg-brand-light px-5 py-4 text-sm">
        <span className="text-zinc-600">백엔드 API 상태: </span>
        <Suspense fallback={<span className="text-zinc-400">확인 중…</span>}>
          <ApiStatus />
        </Suspense>
      </div>
    </main>
  );
}
