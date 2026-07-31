# app-bana-runtime/Dockerfile
# Build from the repository root:
#   docker build -f app-bana-runtime/Dockerfile -t app-bana-runtime .

# ─── Build stage ─────────────────────────────────────────────────────────────
FROM node:lts-alpine AS builder

# Install pnpm via npm (compatible with lockfile v9)
RUN npm install -g pnpm@9

WORKDIR /build

# Copy workspace manifest files for layer-cached dependency install
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml .npmrc ./

# Copy workspace packages that are needed for the runtime build
COPY app-bana-shared/package.json ./app-bana-shared/
COPY app-bana-runtime/package.json ./app-bana-runtime/

# Install all workspace dependencies (frozen lockfile for reproducibility)
RUN pnpm install --frozen-lockfile

# Copy source for the shared library and runtime
COPY app-bana-shared/ ./app-bana-shared/
COPY app-bana-runtime/ ./app-bana-runtime/

# Build the runtime (TypeScript + Vite → dist/)
RUN pnpm --filter @appbana/runtime build

# ─── Runtime stage ───────────────────────────────────────────────────────────
FROM nginx:stable-alpine

COPY --from=builder /build/app-bana-runtime/dist /usr/share/nginx/html
COPY app-bana-runtime/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
