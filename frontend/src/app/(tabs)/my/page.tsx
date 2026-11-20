import type { Metadata } from "next";

import { TabPlaceholder } from "@/components/layout/TabPlaceholder";

export const metadata: Metadata = { title: "마이" };

export default function MyPage() {
  return <TabPlaceholder title="마이" description="피부 프로필과 선호·기피 성분을 관리해요." />;
}
