import type { Metadata } from "next";
import Link from "next/link";

import { buttonClassName } from "@/components/ui/Button";
import { ErrorState } from "@/components/ui/ErrorState";

export const metadata: Metadata = { title: "페이지를 찾을 수 없어요" };

/** 없는 주소로 들어오거나 notFound()가 호출됐을 때 (HTTP 404) */
export default function NotFound() {
  return (
    <div className="mx-auto flex min-h-dvh w-full max-w-app flex-1 flex-col">
      <ErrorState
        icon="compass"
        title="페이지를 찾을 수 없어요"
        description={"주소가 바뀌었거나 삭제된 페이지예요.\n입력한 주소를 다시 확인해 주세요."}
        actions={
          <Link href="/" className={buttonClassName("primary")}>
            홈으로
          </Link>
        }
      />
    </div>
  );
}
