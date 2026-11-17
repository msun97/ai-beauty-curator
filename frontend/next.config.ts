import type { NextConfig } from "next";

const API_BASE_URL = process.env.API_BASE_URL ?? "http://localhost:8090";

const nextConfig: NextConfig = {
  // 브라우저는 Next.js 도메인으로만 요청하고 /api/** 는 백엔드로 전달한다.
  // 인증 쿠키가 프론트엔드 도메인의 first-party 쿠키가 되도록 하기 위함 (ADR-004).
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${API_BASE_URL}/api/:path*` }];
  },
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
