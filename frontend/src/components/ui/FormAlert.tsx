import { Icon } from "./Icon";
import { TraceId } from "./TraceId";

/**
 * 특정 입력란이 아닌 폼 전체 오류 (예: "이메일 또는 비밀번호가 올바르지 않아요").
 * role="alert"라 나타나는 즉시 스크린 리더가 읽는다.
 */
export function FormAlert({ message, traceId }: { message?: string | null; traceId?: string | null }) {
  if (!message) return null;
  return (
    <div role="alert" className="flex gap-2 rounded-md bg-danger-bg px-4 py-3 text-danger">
      <Icon name="alert" size={18} className="mt-0.5 shrink-0" />
      <div>
        <p className="text-sm font-medium">{message}</p>
        <TraceId value={traceId} className="mt-0.5" />
      </div>
    </div>
  );
}
