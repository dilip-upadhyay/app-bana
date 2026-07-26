import type { AppMeta, PageMeta, TenantBranding, AppContext, EntitySchema } from './metadata';

const BACKEND = 'http://localhost:8080';
const AI_BUILDER = 'http://localhost:8081';

// ── Auth ──────────────────────────────────────────────────────────────────────

export interface AuthResult {
  token: string;
  userId: string;
  email: string;
  name: string;
  tenantId: string;
}

export async function login(email: string, password: string): Promise<AuthResult> {
  const res = await fetch(`${BACKEND}/api/auth/login`, {
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
  const res = await fetch(`${BACKEND}/api/auth/register`, {
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

// ── Branding & Context ────────────────────────────────────────────────────────

export async function fetchBranding(tenantId: string): Promise<TenantBranding> {
  const res = await fetch(`${BACKEND}/api/tenants/${encodeURIComponent(tenantId)}/branding`);
  if (!res.ok) return { tenantId, displayName: 'AppBana', logoUrl: null, primaryColor: '#6366f1' };
  return res.json();
}

export async function fetchAppContext(tenantId: string, appId: string): Promise<AppContext> {
  const params = new URLSearchParams({ tenantId, appId });
  const res = await fetch(`${BACKEND}/api/app-context?${params}`);
  if (!res.ok) throw new Error(`Failed to fetch app context: ${res.status}`);
  return res.json();
}

// ── Apps ──────────────────────────────────────────────────────────────────────

export async function listApps(tenantId: string, token: string): Promise<AppMeta[]> {
  const res = await fetch(`${BACKEND}/appbana-studio/${tenantId}/apps`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error(`Failed to list apps: ${res.status}`);
  const data = await res.json();
  return Array.isArray(data) ? data : (data.apps ?? []);
}

export async function getApp(tenantId: string, appId: string, token: string): Promise<AppMeta> {
  const res = await fetch(`${BACKEND}/appbana-studio/${tenantId}/apps/${appId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error(`Failed to get app: ${res.status}`);
  return res.json();
}

export async function createApp(tenantId: string, name: string, token: string): Promise<AppMeta> {
  const res = await fetch(`${BACKEND}/appbana-studio/${tenantId}/apps`, {
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
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: '{}',
  });
  if (!res.ok) {
    let detail = '';
    try { detail = ' — ' + (await res.text()); } catch { /* ignore */ }
    throw new Error(`Deploy failed: ${res.status}${detail}`);
  }
  return res.json();
}

// ── Pages ─────────────────────────────────────────────────────────────────────

export async function getPage(tenantId: string, appId: string, pageId: string, token: string): Promise<PageMeta> {
  const res = await fetch(`${BACKEND}/appbana-studio/${tenantId}/apps/${appId}/pages/${pageId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error(`Failed to get page: ${res.status}`);
  return res.json();
}

// ── Entities ─────────────────────────────────────────────────────────────────

export async function listEntities(tenantId: string, appId: string, token: string): Promise<EntitySchema[]> {
  const schemaKey = `${tenantId}_${appId}`;
  const res = await fetch(`${BACKEND}/schema`, {
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
  const res = await fetch(`${BACKEND}/schema/${encodeURIComponent(schemaKey)}`, {
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
  const res = await fetch(`${BACKEND}/api/${entityKey}?${qs}`, {
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
    const res = await fetch(`${BACKEND}/api/${entityKey}?_count=true`, {
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
  const res = await fetch(`${BACKEND}/api/${entityKey}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(row),
  });
  if (!res.ok) {
    const body = await res.text().catch(() => '');
    throw new Error(`Insert failed: ${res.status} ${body}`);
  }
  return res.json();
}

// ── Chat sessions ─────────────────────────────────────────────────────────────

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
  const res = await fetch(`${AI_BUILDER}/api/ai/chat/sessions?${qs}`, {
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
  const res = await fetch(`${AI_BUILDER}/api/ai/chat/history?${qs}`, {
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
  const res = await fetch(`${AI_BUILDER}/api/ai/chat/sessions/${encodeURIComponent(sessionId)}`, {
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
  const res = await fetch(`${AI_BUILDER}/api/ai/chat/sessions/${encodeURIComponent(sessionId)}?${qs}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error(`Delete failed: ${res.status}`);
}

// ── SSE streaming chat ────────────────────────────────────────────────────────

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

/**
 * Open an SSE stream to the agent and yield typed events.
 * The caller can signal abort via the AbortController signal.
 */
export async function* streamAgentChat(
  payload: ChatPayload,
  signal: AbortSignal
): AsyncGenerator<SseEvent> {
  const res = await fetch(`${AI_BUILDER}/api/ai/chat/agent/stream`, {
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
        // malformed frame — skip
      }
    }
  }
}
