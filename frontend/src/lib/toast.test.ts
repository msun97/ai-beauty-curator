import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "./api/errors";
import { MAX_TOASTS, getToasts, resetToasts, subscribeToasts, toast } from "./toast";

describe("toast", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    resetToasts();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it("띄운 토스트는 시간이 지나면 자동으로 닫힌다", () => {
    toast.success("저장했어요");
    expect(getToasts()).toMatchObject([{ type: "success", message: "저장했어요" }]);

    vi.advanceTimersByTime(3500);
    expect(getToasts()).toEqual([]);
  });

  it("오류 토스트는 더 오래 보인다", () => {
    toast.error("실패했어요");
    vi.advanceTimersByTime(3500);
    expect(getToasts()).toHaveLength(1);
    vi.advanceTimersByTime(2500);
    expect(getToasts()).toEqual([]);
  });

  it("ApiError를 넘기면 백엔드 문구와 trace ID를 보여준다", () => {
    toast.error(new ApiError({ status: 409, code: "AUTH_EMAIL_DUPLICATED", message: "이미 가입된 이메일이에요.", traceId: "abc123" }));
    expect(getToasts()[0]).toMatchObject({ type: "error", message: "이미 가입된 이메일이에요.", traceId: "abc123" });
  });

  it("일반 Error는 내부 메시지를 노출하지 않는다", () => {
    toast.error(new Error("TypeError: cannot read properties of undefined"));
    expect(getToasts()[0].message).not.toContain("TypeError");
  });

  it("최대 개수를 넘으면 가장 오래된 토스트부터 닫는다", () => {
    for (let i = 1; i <= MAX_TOASTS + 1; i++) toast.info(`알림 ${i}`);
    expect(getToasts().map((t) => t.message)).toEqual(["알림 2", "알림 3", "알림 4"]);
  });

  it("같은 문구는 쌓지 않고 하나만 남긴다", () => {
    toast.error("연결이 원활하지 않아요");
    toast.error("연결이 원활하지 않아요");
    expect(getToasts()).toHaveLength(1);
  });

  it("dismiss로 직접 닫고, 변경 시 구독자에게 알린다", () => {
    const listener = vi.fn();
    const unsubscribe = subscribeToasts(listener);
    const id = toast.info("안내");
    toast.dismiss(id);

    expect(getToasts()).toEqual([]);
    expect(listener).toHaveBeenCalledTimes(2);
    unsubscribe();
  });
});
