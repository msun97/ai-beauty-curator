/** 아직 구현 전인 탭 화면의 임시 내용. 각 기능 주차에 실제 화면으로 교체한다. */
export function TabPlaceholder({ title, description }: { title: string; description: string }) {
  return (
    <main className="flex flex-1 flex-col px-screen pt-8">
      <h1 className="text-2xl font-bold">{title}</h1>
      <p className="mt-2 text-sm text-muted">{description}</p>
      <div className="mt-10 rounded-card bg-surface-muted px-5 py-10 text-center text-sm text-muted">
        화면을 준비하고 있어요
      </div>
    </main>
  );
}
