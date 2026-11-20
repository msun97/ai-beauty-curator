import { SITE } from "@/lib/site";

/** 서비스 로고: "pick"은 브랜드 색, "ly"는 진한 글자색 (Figma 시안 기준) */
export function Logo({ className = "text-2xl" }: { className?: string }) {
  return (
    <span className={`font-display font-extrabold tracking-tight ${className}`} aria-label={SITE.name}>
      <span className="text-brand">pick</span>
      <span className="text-ink-strong">ly</span>
    </span>
  );
}
