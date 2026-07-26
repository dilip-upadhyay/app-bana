import type { AppMeta, PageMeta, TenantBranding, AppContext, EntitySchema } from './metadata';

const BACKEND = 'http://localhost:8080';
const AI_BUILDER = 'http://localhost:8081';

/**
 * Wrapped fetch that broadcasts a `appbana:auth:expired` browser event whenever
 * the backend returns 401. Listeners (e.g. AuthGate in the studio) can then
 * clear the persisted session and force the user to re-login instead of
 * silently swallowing the failure and showing an empty UI.
 *
 * Use this in place of `fetch()` for any authed backend call so we get one
 * consistent recovery path for expired / invalidated tokens (common after
 * the ai-builder or app-bana-service is restarted).
 */
export async function authedFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const res = await fetch(input, init);
  if (res.status === 401 && typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('appbana:auth:expired'));
  }
  return res;
}

// â”€â”€ Auth â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export interface AuthResult {
  token: string;
  userId: string;
  email: string;
  name: string;
  tenantId: string;
}

export async function login(email: string, password: string): Promise<AuthResult> {
  const res = await authedFetch(`${BACKEND}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  if (!res.ok) throw new Error(`Login failed: ${res.status}`);
  // Backend returns { token, sessionId, user: { id, email, name, tenantId, ... }, message }
  const body = await res.json();
  return {
    token: body.token ?? body.sessionId,
    userId: String(body.user?.id ?? ''),
    email: body.user?.email ?? email,
    name: body.user?.name ?? '',
    tenantId: body.user?.tenantId ?? 'default',
  };
}

export async function register(name: string, email: string, password: string): Promise<AuthResult> {
  const res = await authedFetch(`${BACKEND}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, email, password }),
  });
  if (!res.ok) throw new Error(`Registration failed: ${res.status}`);
  // Backend returns { token, sessionId, user: { id, email, name, tenantId, ... }, message }
  const body = await res.json();
  return {
    token: body.token ?? body.sessionId,
    userId: String(body.user?.id ?? ''),
    email: body.user?.email ?? email,
    name: body.user?.name ?? name,
    tenantId: body.user?.tenantId ?? 'default',
  };
}

// â”€â”€ Branding & Context â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export async function fetchBranding(tenantId: string): Promise<TenantBranding> {
  const res = await authedFetch(`${BACKEND}/api/tenants/${encodeURIComponent(tenantId)}/branding`);
  if (!res.ok) return { tenantId, displayName: 'AppBana', logoUrl: null, primaryColor: '#6366f1' };
  return res.json();
}

export async function fetchAppContext(tenantId: string, appId: string): Promise<AppContext> {
  const params = new URLSearchParams({ tenantId, appId });
  const res = await authedFetch(`${BACKEND}/api/app-context?${params}`);
  if (!res.ok) throw new Error(`Failed to fetch app context: ${res.status}`);
  return res.json();
}

// â”€â”€ Apps â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export async function listApps(tenantId: string, token: string): Promise<AppMeta[]> {
  const res = await authedFetch(`${BACKEND}/appbana-studio/${tenantId}/apps`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error(`Failed to list apps: ${res.status}`);
  const data = await res.json();
  return Array.isArray(data) ? data : (data.apps ?? []);
}

export async function getApp(tenantId: string, appId: string, token: string): Promise<AppMeta> {
  const res = await authedFetch(`${BACKEND}/appbana-studio/${tenantId}/apps/${appId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error(`Failed to get app: ${res.status}`);
  return res.json();
}

export async function createApp(tenantId: string, name: string, token: string): Promise<AppMeta> {
  const res = await authedFetch(`${BACKEND}/appbana-studio/${tenantId}/apps`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ name }),
  });
  if (!res.ok) throw new Error(`Failed to create app: ${res.status}`);
  return res.json();
}

export interface DeployResult {
  success: boolean;
  environment: string;
  version: number;
  versionId: number;
  durationMs: number;
  summary: string;
  tablesCreated: string[];
}

export async function deployApp(
  tenantId: string,
  appId: string,
  token: string,
  environment = 'DEV'
): Promise<DeployResult> {
  // Backend expects `env` as a **query parameter**, not a body field.
  // See AppRoutes.java:  router.post("/api/{tenantId}/apps/{id}/publish") -> req.query("env").
  const url =
    `${BACKEND}/api/${encodeURIComponent(tenantId)}/apps/${encodeURIComponent(appId)}/publish` +
    `?env=${encodeURIComponent(environment)}`;
  const res = await authedFetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: '{}',
  });
  if (!res.ok) {
    let detail = '';
    try { detail = ' â€” ' + (await res.text()); } catch { /* ignore */ }
    throw new Error(`Deploy failed: ${res.status}${detail}`);
  }
  return res.json();
}

// â”€â”€ Pages â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export async function getPage(tenantId: string, appId: string, pageId: string, token: string): Promise<PageMeta> {
  const res = await authedFetch(`${BACKEND}/appbana-studio/${tenantId}/apps/${appId}/pages/${pageId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error(`Failed to get page: ${res.status}`);
  return res.json();
}

// â”€â”€ Entities â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export async function listEntities(tenantId: string, appId: string, token: string): Promise<EntitySchema[]> {
  const schemaKey = `${tenantId}_${appId}`;
  const res = await authedFetch(`${BACKEND}/schema`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error(`Failed to list schemas: ${res.status}`);
  const names: string[] = await res.json();
  return names
    .filter((n: string) => n.startsWith(schemaKey))
    .map((n: string) => ({ name: n.replace(`${schemaKey}_`, ''), tenantId, appId, fields: [] }));
}

/**
 * Fetch a full entity schema by its fully-qualified key (`{tenantId}_{appId}_{entityName}`).
 * Used by the studio Data Drawer to build the "Add row" form.
 */
export async function getEntitySchema(schemaKey: string, token: string): Promise<EntitySchema> {
  const res = await authedFetch(`${BACKEND}/schema/${encodeURIComponent(schemaKey)}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error(`Failed to load schema ${schemaKey}: ${res.status}`);
  return res.json();
}

export async function fetchEntityRows(
  entityKey: string,
  token: string,
  params: Record<string, string | number> = {}
): Promise<{ rows: any[]; total: number }> {
  const qs = new URLSearchParams(Object.entries(params).map(([k, v]) => [k, String(v)]));
  const res = await authedFetch(`${BACKEND}/api/${entityKey}?${qs}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error(`Failed to fetch rows: ${res.status}`);
  const data = await res.json();
  return {
    rows: Array.isArray(data) ? data : (data.rows ?? data.data ?? []),
    total: data.total ?? data.count ?? (Array.isArray(data) ? data.length : 0),
  };
}

/**
 * Cheap row-count only fetch. Uses ?_count=true which asks the backend to skip
 * row hydration. Falls back to a small paged fetch if _count is unsupported.
 */
export async function fetchEntityRowCount(entityKey: string, token: string): Promise<number> {
  try {
    const res = await authedFetch(`${BACKEND}/api/${entityKey}?_count=true`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) return 0;
    const data = await res.json();
    // Backend may return { count: N } or { total: N } or a raw number
    if (typeof data === 'number') return data;
    if (typeof data.count === 'number') return data.count;
    if (typeof data.total === 'number') return data.total;
    return 0;
  } catch {
    return 0;
  }
}

/** Insert a single row into a dynamic entity table. */
export async function insertEntityRow(
  entityKey: string,
  row: Record<string, unknown>,
  token: string
): Promise<Record<string, unknown>> {
  const res = await authedFetch(`${BACKEND}/api/${entityKey}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(row),
  });
  if (!res.ok) {
    await throwEntityError(res, 'Insert failed');
  }
  return res.json();
}

/**
 * Sprint 3 task 3.4 — Update a single row via `PUT /api/{entity}/{id}`.
 * Backend returns `{ updated: number }`. Throws {@link ApiFieldError} on 400
 * so the caller can render inline field errors.
 */
export async function updateEntityRow(
  entityKey: string,
  id: string | number,
  row: Record<string, unknown>,
  token: string
): Promise<{ updated: number }> {
  const res = await authedFetch(`${BACKEND}/api/${entityKey}/${encodeURIComponent(String(id))}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(row),
  });
  if (!res.ok) {
    await throwEntityError(res, 'Update failed');
  }
  return res.json();
}

/**
 * Sprint 3 task 3.5 — Delete a single row via `DELETE /api/{entity}/{id}`.
 * Returns `{ deleted: number }` from the backend.
 */
export async function deleteEntityRow(
  entityKey: string,
  id: string | number,
  token: string
): Promise<{ deleted: number }> {
  const res = await authedFetch(`${BACKEND}/api/${entityKey}/${encodeURIComponent(String(id))}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) {
    await throwEntityError(res, 'Delete failed');
  }
  return res.json();
}

/**
 * Sprint 3 task 3.3 — Fetch a single row by id. Used by DetailPage to
 * hydrate the entity form in view / edit mode.
 */
export async function getEntityRow(
  entityKey: string,
  id: string | number,
  token: string
): Promise<Record<string, unknown> | null> {
  const res = await authedFetch(`${BACKEND}/api/${entityKey}/${encodeURIComponent(String(id))}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (res.status === 404) return null;
  if (!res.ok) {
    await throwEntityError(res, 'Fetch failed');
  }
  return res.json();
}

/**
 * Sprint 3 task 3.1 — Structured error thrown by the entity mutation helpers
 * when the backend returns HTTP 4xx. `fieldErrors` mirrors the backend's
 * `errors: { fieldName: reason }` map when present; otherwise it's empty and
 * the caller should surface `.message` at form level.
 *
 * See {@link com.appbana.service.ErrorHandler#fieldValidationError} on the
 * backend for the response contract.
 */
export class ApiFieldError extends Error {
  readonly status: number;
  readonly fieldErrors: Record<string, string>;
  constructor(message: string, status: number, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.name = 'ApiFieldError';
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

/**
 * Read a mutation Response's body, normalise it into either an
 * {@link ApiFieldError} (when the backend returned a structured `errors`
 * map) or a plain Error, and throw. Never returns.
 */
async function throwEntityError(res: Response, prefix: string): Promise<never> {
  const raw = await res.text().catch(() => '');
  let parsed: unknown;
  try {
    parsed = raw ? JSON.parse(raw) : null;
  } catch {
    parsed = null;
  }
  const body = (parsed ?? {}) as { error?: string; errors?: Record<string, string> };
  const message = body.error ?? raw ?? `${prefix}: ${res.status}`;
  if (body.errors && typeof body.errors === 'object') {
    throw new ApiFieldError(message, res.status, body.errors);
  }
  // Non-structured 4xx / 5xx — still throw as a plain Error but preserve
  // the status so callers can distinguish network from validation failures.
  const err = new Error(`${prefix}: ${res.status} ${message}`);
  (err as Error & { status?: number }).status = res.status;
  throw err;
}

// â”€â”€ Chat sessions â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export interface ChatSession {
  sessionId: string;
  title?: string | null;
  appId?: string | null;
  lastActivity?: number;
  turnCount?: number;
}

export interface ChatHistoryMessage {
  role: 'user' | 'assistant';
  content: string;
  timestamp: number | null;
}

export async function listSessions(
  userId: string,
  token: string,
  opts: { appId?: string; limit?: number } = {}
): Promise<ChatSession[]> {
  const qs = new URLSearchParams({ userId, limit: String(opts.limit ?? 20) });
  if (opts.appId) qs.set('appId', opts.appId);
  const res = await authedFetch(`${AI_BUILDER}/api/ai/chat/sessions?${qs}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) return [];
  const data = await res.json();
  return Array.isArray(data) ? data : (data.sessions ?? []);
}

export async function getSessionHistory(
  userId: string,
  sessionId: string,
  token: string
): Promise<ChatHistoryMessage[]> {
  const qs = new URLSearchParams({ userId, sessionId });
  const res = await authedFetch(`${AI_BUILDER}/api/ai/chat/history?${qs}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) return [];
  const data = await res.json();
  return data.messages ?? [];
}

export async function renameSession(
  sessionId: string,
  userId: string,
  title: string,
  token: string
): Promise<void> {
  const res = await authedFetch(`${AI_BUILDER}/api/ai/chat/sessions/${encodeURIComponent(sessionId)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ userId, title }),
  });
  if (!res.ok) throw new Error(`Rename failed: ${res.status}`);
}

export async function deleteSession(
  sessionId: string,
  userId: string,
  token: string
): Promise<void> {
  const qs = new URLSearchParams({ userId });
  const res = await authedFetch(`${AI_BUILDER}/api/ai/chat/sessions/${encodeURIComponent(sessionId)}?${qs}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error(`Delete failed: ${res.status}`);
}

// â”€â”€ SSE streaming chat â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export interface ChatPayload {
  message: string;
  sessionId: string;
  userId: string;
  tenantId: string;
  appId: string;
  appName?: string;
  token?: string;
  provider?: string;
  images?: string[];
  context?: {
    selections?: Array<{
      pageId: string;
      nodeId: string;
      entity?: string;
      field?: string;
      screenshot?: string;
    }>;
  };
}

export type SseEvent =
  | { event: 'state';           data: { conversationState: string } }
  | { event: 'token';           data: { text: string } }
  | { event: 'tool_call_start'; data: { id: string; name: string; args: unknown } }
  | { event: 'tool_call_end';   data: { id: string; status: 'ok' | 'error'; result: unknown } }
  | { event: 'done';            data: { conversationId: string; finalMessage: string } };

// ─── Phase B5 — Saved views ────────────────────────────────────────────────

export interface SavedViewRecord {
  viewId: string;
  name: string;
  view: {
    filters?: Record<string, unknown>;
    groupBy?: string;
    sort?: { field: string; direction: 'asc' | 'desc' };
    [k: string]: unknown;
  };
  isDefault?: boolean;
  ownerUserId?: string | null;
}

export async function listSavedViews(
  tenantId: string,
  appId: string,
  entityKey: string,
  token: string
): Promise<SavedViewRecord[]> {
  const qs = new URLSearchParams({ tenantId, appId, entityKey }).toString();
  const res = await authedFetch(`${BACKEND}/api/saved-views?${qs}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error(`Failed to list saved views: ${res.status}`);
  const body = (await res.json()) as { views?: SavedViewRecord[] };
  return body.views ?? [];
}

export async function saveView(
  input: {
    tenantId: string;
    appId: string;
    entityKey: string;
    name: string;
    view: SavedViewRecord['view'];
    isDefault?: boolean;
    ownerUserId?: string | null;
  },
  token: string
): Promise<{ viewId: string }> {
  const res = await authedFetch(`${BACKEND}/api/saved-views`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(input),
  });
  if (!res.ok) throw new Error(`Failed to save view: ${res.status}`);
  return (await res.json()) as { viewId: string };
}

export async function deleteSavedView(viewId: string, token: string): Promise<void> {
  const res = await authedFetch(`${BACKEND}/api/saved-views/${encodeURIComponent(viewId)}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok && res.status !== 404) {
    throw new Error(`Failed to delete view: ${res.status}`);
  }
}

/**
 * Open an SSE stream to the agent and yield typed events.
 * The caller can signal abort via the AbortController signal.
 */
export async function* streamAgentChat(
  payload: ChatPayload,
  signal: AbortSignal
): AsyncGenerator<SseEvent> {
  const res = await authedFetch(`${AI_BUILDER}/api/ai/chat/agent/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
    signal,
  });

  if (!res.ok) throw new Error(`Chat stream failed: ${res.status}`);
  if (!res.body) throw new Error('Response body is null');

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    const frames = buffer.split('\n\n');
    buffer = frames.pop() ?? '';

    for (const frame of frames) {
      const eventLine = /^event:\s*([^\n\r]+)/m.exec(frame)?.[1]?.trim();
      const dataLine  = /^data:\s*([^\n\r]+)/m.exec(frame)?.[1]?.trim();
      if (!eventLine || !dataLine) continue;
      try {
        const data = JSON.parse(dataLine);
        yield { event: eventLine, data } as SseEvent;
      } catch {
        // malformed frame â€” skip
      }
    }
  }
}
