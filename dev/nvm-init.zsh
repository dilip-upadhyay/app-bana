ls# nvm-init.zsh -- Helper snippet for loading Homebrew-installed nvm on macOS (Apple Silicon / Intel)
# Copy or 'source' this file from your ~/.zshrc to ensure nvm & Node versions from .nvmrc are auto‑loaded.
#
# INSTALL LOCATION NOTES
#  Homebrew Cellar path you provided: /opt/homebrew/Cellar/nvm/0.40.3
#  Homebrew exposes a stable symlink: /opt/homebrew/opt/nvm
#  We prefer the /opt/homebrew/opt/nvm path so future upgrades do not require edits.
#
# BASIC USAGE (append to ~/.zshrc):
#   source /Users/dilipupadhyay/git/app-bana/dev/nvm-init.zsh
#
# After adding: restart terminal or `exec zsh`, then run: `node -v` and `nvm --version`.
# Optional: create a default Node version (e.g. the current LTS) with: nvm alias default node

# 1. Set and create NVM_DIR if missing
export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"
mkdir -p "$NVM_DIR"

# 2. Source nvm (prefer Homebrew opt symlink; fallback to explicit Cellar path you gave)
if [ -s "/opt/homebrew/opt/nvm/nvm.sh" ]; then
  . "/opt/homebrew/opt/nvm/nvm.sh"
elif [ -s "/opt/homebrew/Cellar/nvm/0.40.3/nvm.sh" ]; then
  . "/opt/homebrew/Cellar/nvm/0.40.3/nvm.sh"
fi

# 3. (Optional) Load nvm bash_completion for better tab completion in zsh
if [ -s "/opt/homebrew/opt/nvm/etc/bash_completion.d/nvm" ]; then
  . "/opt/homebrew/opt/nvm/etc/bash_completion.d/nvm"
fi

# 4. Auto-use .nvmrc when entering directories (add_zsh_hook only once)
if command -v nvm >/dev/null 2>&1; then
  autoload -U add-zsh-hook
  load-nvmrc() {
    # Find closest .nvmrc (current dir up to root)
    local nvmrc="$(nvm_find_nvmrc 2>/dev/null)" || true
    if [ -n "$nvmrc" ]; then
      local node_version
      node_version="$(< "$nvmrc")"
      # If current doesn't match desired .nvmrc version, switch
      if [ "$(nvm version)" != "$(nvm version "$node_version")" ]; then
        nvm install "$node_version" >/dev/null 2>&1
        nvm use "$node_version" >/dev/null 2>&1 && echo "(nvm) using Node $(node -v) from $nvmrc" >&2
      fi
    fi
  }
  add-zsh-hook chpwd load-nvmrc
  # Run once on shell start
  load-nvmrc
fi

# 5. Fallback if node still not present
if ! command -v node >/dev/null 2>&1; then
  echo "[nvm-init] WARNING: node command not found after sourcing nvm. Verify Homebrew install or path." >&2
fi

