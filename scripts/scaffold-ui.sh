#!/usr/bin/env bash
# Scaffold Angular 21 workspace under app-bana/ui and integrate Nx per Angular AI guide
# Usage: from repo root
#   chmod +x scripts/scaffold-ui.sh
#   ./scripts/scaffold-ui.sh

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
UI_DIR="$ROOT_DIR/ui"

# zsh-safe echo function
echo_info() { printf "\033[1;34m[info]\033[0m %s\n" "$*"; }
echo_warn() { printf "\033[1;33m[warn]\033[0m %s\n" "$*"; }
echo_err()  { printf "\033[1;31m[err ]\033[0m %s\n" "$*"; }

cd "$ROOT_DIR"
echo_info "Repo root: $ROOT_DIR"

# Ensure NVM and Node LTS (per .nvmrc)
if ! command -v nvm >/dev/null 2>&1; then
  echo_warn "nvm not found — installing nvm (https://github.com/nvm-sh/nvm)"
  # Install NVM into ~/.nvm and update shell profile (zsh)
  export NVM_DIR="$HOME/.nvm"
  mkdir -p "$NVM_DIR"
  curl -fsSL https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash
  # Source nvm for current shell session
  if [ -s "$NVM_DIR/nvm.sh" ]; then
    # shellcheck disable=SC1090
    . "$NVM_DIR/nvm.sh"
  fi
  if [ -s "$NVM_DIR/bash_completion" ]; then
    # shellcheck disable=SC1090
    . "$NVM_DIR/bash_completion"
  fi
else
  # Ensure NVM is loaded in non-login shells too
  export NVM_DIR="$HOME/.nvm"
  if [ -s "$NVM_DIR/nvm.sh" ]; then
    # shellcheck disable=SC1090
    . "$NVM_DIR/nvm.sh"
  fi
fi

# Use Node LTS as specified by .nvmrc (lts/*)
if [ -f "$ROOT_DIR/.nvmrc" ]; then
  echo_info "Using Node version from .nvmrc: $(cat "$ROOT_DIR/.nvmrc" | tr -d '\n')"
  nvm install
  nvm use
else
  echo_warn ".nvmrc not found; installing latest LTS"
  nvm install --lts
  nvm use --lts
fi

echo_info "Node: $(node -v) | npm: $(npm -v)"

# Ensure ui directory exists and enter it
mkdir -p "$UI_DIR"
cd "$UI_DIR"

echo_info "Target UI directory: $UI_DIR"

# If no package.json, create Angular workspace in-place per Angular AI guide
if [ ! -f "package.json" ]; then
  echo_info "Scaffolding Angular workspace via 'npm create @angular@latest' (non-interactive)"
  # Backup existing README to avoid overwrite by Angular new
  if [ -f "README.md" ] && [ ! -f "README.pre-angular.md" ]; then
    mv README.md README.pre-angular.md
    echo_info "Backed up existing ui/README.md to ui/README.pre-angular.md"
  fi
  # Create an app named 'studio' into current directory using --directory .
  npm create @angular@latest studio -- \
    --directory . \
    --routing \
    --style=scss \
    --skip-git \
    --skip-install \
    --package-manager npm \
    --ssr=false \
    --standalone \
    --force \
    --no-interactive
else
  echo_warn "package.json exists; skipping Angular scaffold"
fi

# Install dependencies if node_modules missing
if [ ! -d "node_modules" ]; then
  echo_info "Installing npm dependencies"
  npm install
fi

# Integrate Nx into the Angular workspace (skip Nx Cloud prompt)
if [ ! -f "nx.json" ]; then
  echo_info "Adding Nx to Angular workspace (ng add @nx/angular --nxCloud=skip)"
  # ng add may prompt for analytics; --no-interactive avoids prompts
  npx ng add @nx/angular --nxCloud=skip --no-interactive || {
    echo_warn "ng add @nx/angular failed once; retrying with --skip-confirmation"
    npx ng add @nx/angular --nxCloud=skip --no-interactive --skip-confirmation || true
  }
else
  echo_warn "nx.json exists; Nx already integrated — skipping"
fi

# Ensure @nx/angular is installed (in case ng add was partial)
if ! npm ls @nx/angular >/dev/null 2>&1; then
  echo_info "Installing @nx/angular dev dependency"
  npm i -D @nx/angular@latest
fi

# Generate libraries if missing
if [ -f "nx.json" ]; then
  [ -d "libs/runtime" ] || npx nx g @nx/angular:library runtime --standalone --no-interactive
  [ -d "libs/designer" ] || npx nx g @nx/angular:library designer --standalone --no-interactive
  [ -d "libs/ui-schema" ] || npx nx g @nx/angular:library ui-schema --standalone --no-interactive
else
  echo_warn "Nx not detected; skipping lib generation"
fi

# Create styling tokens/utilities in apps/studio if present
STUDIO_STYLES_DIR="apps/studio/src/styles"
TOKENS_CSS="$STUDIO_STYLES_DIR/tokens.css"
UTILS_CSS="$STUDIO_STYLES_DIR/utilities.css"

if [ -d "apps/studio" ]; then
  mkdir -p "$STUDIO_STYLES_DIR"
  if [ ! -f "$TOKENS_CSS" ]; then
    cat > "$TOKENS_CSS" << 'EOF'
:root {
  --color-bg: #ffffff;
  --color-text: #111111;
  --color-primary: #1e88e5;
  --color-primary-contrast: #ffffff;
  --color-surface: #f7f7f8;
  --space-0: 0px; --space-1: 4px; --space-2: 8px; --space-3: 12px; --space-4: 16px; --space-5: 20px; --space-6: 24px; --space-7: 28px; --space-8: 32px;
  --radius-sm: 4px; --radius-md: 8px; --radius-lg: 12px;
  --font-sans: ui-sans-serif, system-ui, -apple-system, Segoe UI, Roboto, Arial, Noto Sans, "Apple Color Emoji", "Segoe UI Emoji";
  --text-sm: 0.875rem; --text-md: 1rem; --text-lg: 1.125rem;
}
.dark {
  --color-bg: #0b0c0f; --color-text: #e7e9ee; --color-primary: #90caf9; --color-primary-contrast: #0b0c0f; --color-surface: #16181d;
}
EOF
    echo_info "Created $TOKENS_CSS"
  else
    echo_warn "$TOKENS_CSS exists, leaving as-is"
  fi

  if [ ! -f "$UTILS_CSS" ]; then
    cat > "$UTILS_CSS" << 'EOF'
.u-flex { display:flex; }
.u-col { flex-direction:column; }
.u-center { align-items:center; justify-content:center; }
.u-grid { display:grid; }
.u-m-0 { margin: var(--space-0); }
.u-m-1 { margin: var(--space-1); }
.u-m-2 { margin: var(--space-2); }
.u-m-3 { margin: var(--space-3); }
.u-m-4 { margin: var(--space-4); }
.u-p-0 { padding: var(--space-0); }
.u-p-1 { padding: var(--space-1); }
.u-p-2 { padding: var(--space-2); }
.u-p-3 { padding: var(--space-3); }
.u-p-4 { padding: var(--space-4); }
.u-card { background: var(--color-surface); border-radius: var(--radius-md); }
EOF
    echo_info "Created $UTILS_CSS"
  else
    echo_warn "$UTILS_CSS exists, leaving as-is"
  fi

  # Ensure styles.scss imports tokens and utilities
  STUDIO_SCSS="apps/studio/src/styles.scss"
  if [ -f "$STUDIO_SCSS" ]; then
    if ! grep -q "tokens.css" "$STUDIO_SCSS"; then
      printf "@import './styles/tokens.css';\n" >> "$STUDIO_SCSS"
      echo_info "Imported tokens.css into styles.scss"
    fi
    if ! grep -q "utilities.css" "$STUDIO_SCSS"; then
      printf "@import './styles/utilities.css';\n" >> "$STUDIO_SCSS"
      echo_info "Imported utilities.css into styles.scss"
    fi
  else
    echo_warn "$STUDIO_SCSS not found; ensure studio app uses styles.scss and import tokens/utilities manually"
  fi

  # Mirror tokens into runtime lib
  RUNTIME_TOKENS_DIR="libs/runtime/src/lib/styles"
  mkdir -p "$RUNTIME_TOKENS_DIR"
  RUNTIME_TOKENS_CSS="$RUNTIME_TOKENS_DIR/tokens.css"
  if [ ! -f "$RUNTIME_TOKENS_CSS" ] && [ -f "$TOKENS_CSS" ]; then
    cp "$TOKENS_CSS" "$RUNTIME_TOKENS_CSS"
    echo_info "Created $RUNTIME_TOKENS_CSS"
  fi
else
  echo_warn "apps/studio not found yet; you may need to generate it or adjust app name in the scaffold"
fi

# Validate workspace
echo_info "Running build and lint to validate workspace..."
if npm run build; then
  echo_info "Build succeeded"
else
  echo_warn "Build failed — continuing for inspection"
fi
if npm run lint; then
  echo_info "Lint succeeded"
else
  echo_warn "Lint failed — please review lints"
fi

echo_info "Scaffold complete. Start dev server with: npx nx serve studio (or: npm start if configured)"
