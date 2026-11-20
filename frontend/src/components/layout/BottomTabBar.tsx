"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

import { Icon } from "@/components/ui/Icon";

import { TABS, isActiveTab } from "./tabs";

export function BottomTabBar() {
  const pathname = usePathname();

  return (
    <nav
      aria-label="주요 메뉴"
      data-tab-bar
      className="fixed inset-x-0 bottom-0 z-20 mx-auto max-w-app border-t border-line bg-surface/95 pb-[env(safe-area-inset-bottom)] shadow-[0_-8px_24px_rgba(66,35,45,0.04)] backdrop-blur-md"
    >
      <ul className="grid h-tab-bar grid-cols-5 px-2.5">
        {TABS.map((tab) => {
          const active = isActiveTab(pathname, tab.href);
          return (
            <li key={tab.href} className="flex">
              <Link
                href={tab.href}
                aria-current={active ? "page" : undefined}
                className={`flex min-h-11 flex-1 flex-col items-center justify-center gap-1 rounded-md text-xs transition-colors focus-visible:outline-2 focus-visible:outline-offset-[-4px] focus-visible:outline-brand ${
                  active ? "font-bold text-brand" : "text-muted hover:text-ink"
                }`}
              >
                <Icon name={tab.icon} size={22} strokeWidth={active ? 2.2 : 1.8} />
                <span>{tab.label}</span>
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
