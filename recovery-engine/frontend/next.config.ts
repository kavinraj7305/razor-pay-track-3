import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      { source: "/api/:path*", destination: "http://127.0.0.1:8080/api/:path*" },
      { source: "/agent-api/:path*", destination: "http://127.0.0.1:8002/:path*" },
    ];
  },
};

export default nextConfig;
