import type { NextConfig } from "next";
import path from "node:path";

const nextConfig: NextConfig = {
  outputFileTracingRoot: path.join(__dirname),
  async rewrites() {
    return [
      { source: "/api/:path*", destination: "http://127.0.0.1:8080/api/:path*" },
      { source: "/agent-api/:path*", destination: "http://127.0.0.1:8002/:path*" },
    ];
  },
};

export default nextConfig;
