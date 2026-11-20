import type { Metadata } from "next";

import { TabPlaceholder } from "@/components/layout/TabPlaceholder";

export const metadata: Metadata = { title: "검색" };

export default function SearchPage() {
  return <TabPlaceholder title="검색" description="제품, 브랜드, 성분 이름으로 찾아보세요." />;
}
