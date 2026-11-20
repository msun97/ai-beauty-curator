import type { Metadata } from "next";

import { TabPlaceholder } from "@/components/layout/TabPlaceholder";

export const metadata: Metadata = { title: "추천" };

export default function RecommendPage() {
  return <TabPlaceholder title="추천" description="내 피부 프로필에 맞춘 제품을 추천해 드려요." />;
}
