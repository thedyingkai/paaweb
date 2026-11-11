import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  experimental: {
    // The resolver photo archive can be up to 512 MB. Next clones rewrite
    // bodies, and its default 10 MB limit silently truncates larger uploads.
    proxyClientMaxBodySize: "513mb",
    proxyTimeout: 600_000,
  },
  async headers() {
    return [
      {
        source: "/admin/:path*",
        headers: [
          {
            key: "X-Robots-Tag",
            value: "noindex, nofollow, noarchive, nosnippet, noimageindex",
          },
          {
            key: "Cache-Control",
            value: "private, no-store, no-cache, must-revalidate",
          },
        ],
      },
    ];
  },
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: process.env.API_URL ? `${process.env.API_URL}/:path*` : 'http://localhost:8080/:path*',
      },
    ];
  },
  async redirects() {
    return [
      ...['/besti-acm-mark.svg', '/besti-acm-wordmark.svg', '/paa-mark.svg', '/paa-logo.svg', '/icon.svg', '/favicon.ico'].map((source) => ({
        source,
        destination: '/brand/besti-acm-logo-v1.png',
        permanent: true,
      })),
      {
        source: '/home',
        destination: '/',
        permanent: true,
      },
    ];
  },
};

export default nextConfig;
