/**
 * Qualify a bare entity name (e.g. "Customer") into the fully-qualified
 * multi-tenant key ("{tenantId}_{appId}_Customer") that the backend expects.
 *
 * Per copilot-instructions.md Section 8, entity URLs MUST be a single path
 * segment shaped like `{tenantId}_{appId}_{entityName}`. Page metadata often
 * only stores the bare name, so we resolve tenant+app from the URL and
 * prepend the prefix at fetch time. If the caller already passed a qualified
 * key, we detect the prefix and leave it alone.
 *
 * Runtime-only concern (touches `window.location`); NOT exported from
 * `@appbana/shared` which stays platform-neutral.
 */
import { resolveAppContext } from '@appbana/shared';

export function qualifyEntityKey(entityKey: string): string {
  if (!entityKey) return entityKey;
  const ctx = resolveAppContext(window.location);
  if (!ctx) return entityKey;
  const prefix = `${ctx.tenantId}_${ctx.appId}_`;
  if (entityKey.startsWith(prefix)) return entityKey;
  return `${prefix}${entityKey}`;
}

const TOKEN_KEY = 'appbana_token';

export function getRuntimeToken(): string {
  return localStorage.getItem(TOKEN_KEY) ?? '';
}
