import type { Metadata } from "next";

import { TabPlaceholder } from "@/components/layout/TabPlaceholder";

export const metadata: Metadata = { title: "저장" };

export default function SavedPage() {
  return <TabPlaceholder title="저장" description="북마크한 제품을 모아 볼 수 있어요." />;
}
