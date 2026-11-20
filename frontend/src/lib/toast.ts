import { describeError } from "./api/errors";

/**
 * 토스트 알림 저장소.
 * React 밖(API 호출 함수 등)에서도 toast.error(e)처럼 부를 수 있도록 모듈 단위로 상태를 둔다.
 * 화면 표시는 <Toaster />가 useSyncExternalStore로 구독해서 한다.
 */

export type ToastType = "info" | "success" | "error";

export type ToastItem = {
  id: number;
  type: ToastType;
  message: string;
  traceId: string | null;
};

type ShowOptions = { type?: ToastType; traceId?: string | null; durationMs?: number };

/** 동시에 보이는 최대 개수. 넘치면 가장 오래된 것부터 닫는다. */
export const MAX_TOASTS = 3;
const DEFAULT_DURATION_MS = 3500;
/** 오류는 읽고 trace ID를 확인할 시간이 더 필요하다 */
const ERROR_DURATION_MS = 6000;

let toasts: ToastItem[] = [];
let nextId = 1;
const timers = new Map<number, ReturnType<typeof setTimeout>>();
const listeners = new Set<() => void>();
const EMPTY: ToastItem[] = [];

function emit() {
  listeners.forEach((listener) => listener());
}

function dismiss(id: number) {
  clearTimeout(timers.get(id));
  timers.delete(id);
  const next = toasts.filter((t) => t.id !== id);
  if (next.length !== toasts.length) {
    toasts = next;
    emit();
  }
}

function show(message: string, options: ShowOptions = {}): number {
  const type = options.type ?? "info";
  const duration = options.durationMs ?? (type === "error" ? ERROR_DURATION_MS : DEFAULT_DURATION_MS);

  // 같은 문구가 이미 떠 있으면(연속 클릭, 동시 요청 실패) 새로 쌓지 않고 다시 띄운다
  const duplicate = toasts.find((t) => t.message === message && t.type === type);
  if (duplicate) {
    dismiss(duplicate.id);
  }

  const id = nextId++;
  toasts = [...toasts, { id, type, message, traceId: options.traceId ?? null }];
  while (toasts.length > MAX_TOASTS) {
    dismiss(toasts[0].id);
  }
  timers.set(
    id,
    setTimeout(() => dismiss(id), duration),
  );
  emit();
  return id;
}

export const toast = {
  show,
  dismiss,
  info: (message: string) => show(message, { type: "info" }),
  success: (message: string) => show(message, { type: "success" }),
  /** 문자열이면 그대로, 오류 객체(ApiError 등)면 사용자용 문구와 trace ID를 꺼내 보여준다 */
  error: (error: unknown) => {
    if (typeof error === "string") {
      return show(error, { type: "error" });
    }
    const { message, traceId } = describeError(error);
    return show(message, { type: "error", traceId });
  },
};

export function subscribeToasts(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getToasts(): ToastItem[] {
  return toasts;
}

/** 서버 렌더링 시점에는 항상 비어 있다 */
export function getServerToasts(): ToastItem[] {
  return EMPTY;
}

/** 테스트용: 상태 초기화 */
export function resetToasts() {
  timers.forEach((timer) => clearTimeout(timer));
  timers.clear();
  toasts = [];
  emit();
}
