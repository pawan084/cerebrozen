/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // No remote images: every asset ships from /public, and the CSP
  // (middleware.ts) only allows `img-src 'self' data: blob:` anyway.
};

export default nextConfig;
