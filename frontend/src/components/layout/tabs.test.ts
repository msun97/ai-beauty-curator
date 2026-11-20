import { describe, expect, it } from "vitest";

import { TABS, isActiveTab } from "./tabs";

describe("하단 탭 활성 상태", () => {
  it("홈은 정확히 / 일 때만 활성이다", () => {
    expect(isActiveTab("/", "/")).toBe(true);
    expect(isActiveTab("/search", "/")).toBe(false);
  });

  it("하위 경로에서도 해당 탭이 활성이다", () => {
    expect(isActiveTab("/search", "/search")).toBe(true);
    expect(isActiveTab("/my/profile", "/my")).toBe(true);
  });

  it("이름이 비슷한 다른 경로는 활성으로 보지 않는다", () => {
    expect(isActiveTab("/mypage", "/my")).toBe(false);
    expect(isActiveTab("/saved-items", "/saved")).toBe(false);
  });

  it("어떤 경로든 활성 탭은 최대 하나다", () => {
    for (const path of ["/", "/search", "/recommend/1", "/saved", "/my/ingredients", "/login"]) {
      expect(TABS.filter((tab) => isActiveTab(path, tab.href)).length).toBeLessThanOrEqual(1);
    }
  });
});
