# app-bana-studio/Dockerfile
# Build from the repository root:
#   docker build -f app-bana-studio/Dockerfile -t app-bana-studio .

# ─── Build stage ─────────────────────────────────────────────────────────────
FROM node:lts-alpine AS builder

# Install pnpm via npm (compatible with lockfile v9)
RUN npm install -g pnpm@9

WORKDIR /build

# Copy workspace manifest files for layer-cached dependency install
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml .npmrc ./

# Copy workspace packages that are needed for the studio build
COPY app-bana-shared/package.json ./app-bana-shared/
COPY app-bana-studio/package.json ./app-bana-studio/

# Install all workspace dependencies (frozen lockfile for reproducibility)
RUN pnpm install --frozen-lockfile

# Copy source for the shared library and studio
COPY app-bana-shared/ ./app-bana-shared/
COPY app-bana-studio/ ./app-bana-studio/

# Build the studio (TypeScript + Vite → dist/)
RUN pnpm --filter app-bana-studio build

# ─── Runtime stage ───────────────────────────────────────────────────────────
FROM nginx:stable-alpine

COPY --from=builder /build/app-bana-studio/dist /usr/share/nginx/html
COPY app-bana-studio/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
