# Production image: compiles an optimized Next.js build and serves it with
# `next start` (NOT the dev server). NEXT_PUBLIC_* values are inlined at build
# time, so the API URL is passed as a build arg per environment.
#
# Mirrors apps/app/Dockerfile with two differences: port 3003, and no
# `COPY public` — the portal has no static asset directory, and copying a
# missing path fails the build rather than being ignored.
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG NEXT_PUBLIC_API_URL=http://localhost:8000
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
ENV NODE_ENV=production
RUN npm run build

FROM node:20-alpine AS run
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /app/package.json ./
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/.next ./.next
COPY --from=build /app/next.config.mjs ./next.config.mjs
EXPOSE 3003
CMD ["npm", "start"]
