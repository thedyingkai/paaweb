ARG NODE_IMAGE=node:24-bookworm-slim
# 1. 使用构建机原生平台安装编译依赖，避免 QEMU 执行 Next.js 原生编译器。
FROM --platform=$BUILDPLATFORM ${NODE_IMAGE} AS deps
WORKDIR /app

COPY package*.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --include=optional --no-audit --no-fund

# 2. 构建阶段
FROM --platform=$BUILDPLATFORM ${NODE_IMAGE} AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .

ENV NEXT_TELEMETRY_DISABLED=1
ARG API_URL=http://backend:8080
ARG PUBLIC_SITE_URL=http://localhost:3403
ARG NEXT_PUBLIC_SCHOOL_OJ_URL=
ENV API_URL=$API_URL
ENV PUBLIC_SITE_URL=$PUBLIC_SITE_URL
ENV NEXT_PUBLIC_SCHOOL_OJ_URL=$NEXT_PUBLIC_SCHOOL_OJ_URL

# 仅复制编译后的 JavaScript；运行依赖在目标平台重新安装，不能混入构建机原生模块。
RUN --mount=type=cache,id=paa-next-native,target=/app/.next/cache npm run build \
    && test -f .next/standalone/server.js \
    && rm -rf .next/standalone/node_modules

# 3. 目标平台生产依赖（包含对应平台的 Sharp / Next.js 可选原生模块）。
FROM ${NODE_IMAGE} AS prod-deps
WORKDIR /app
COPY package*.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --omit=dev --include=optional --no-audit --no-fund

# 4. 运行阶段
FROM ${NODE_IMAGE} AS runner
WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1

RUN addgroup --system --gid 1001 nodejs
RUN adduser --system --uid 1001 nextjs

COPY --from=builder /app/public ./public
# Keep the same bundled font available to the container and browser.
COPY --from=builder /app/public/fonts /usr/local/share/fonts/cwnupaa-resolver

# 设置预渲染缓存目录的正确权限
RUN mkdir .next
RUN chown nextjs:nodejs .next

# 复制 Next.js 独立输出和静态资源
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=prod-deps --chown=nextjs:nodejs /app/node_modules ./node_modules
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs

EXPOSE 3000

ENV PORT=3000
ENV HOSTNAME="0.0.0.0"

CMD ["node", "server.js"]
