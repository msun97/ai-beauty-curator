/**
 * 오류 문의용 식별자 표시 (NFR-REL-04).
 * 사용자가 고객센터에 알려주면 서버 로그에서 같은 요청을 바로 찾을 수 있다.
 */
export function TraceId({ value, className = "" }: { value: string | null | undefined; className?: string }) {
  if (!value) return null;
  return (
    <p className={`text-xs text-muted ${className}`}>
      오류 코드 <code className="font-display tracking-wide select-all">{value}</code>
    </p>
  );
}
