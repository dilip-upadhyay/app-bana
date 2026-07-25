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
  return res.json();
}

export async function register(name: string, email: string, password: string): Promise<AuthResult> {
  const res = await fetch(`${BACKEND}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, email, password }),
  });
  if (!res.ok) throw new Error(`Registration failed: ${res.status}`);
  return res.json();
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

// ── Chat sessions ─────────────────────────────────────────────────────────────

export interface ChatSession {
  sessionId: string;
  userId: string;
  lastMessage?: string;
  updatedAt?: string;
}

export async function listSessions(token: string): Promise<ChatSession[]> {
  const res = await fetch(`${AI_BUILDER}/api/ai/chat/sessions`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) return [];
  const data = await res.json();
  return Array.isArray(data) ? data : (data.sessions ?? []);
}

// ── SSE streaming chat ────────────────────────────────────────────────────────

export interface ChatPayload {
  message: string;
  sessionId: string;
  userId: string;
  tenantId: string;
  appId: string;
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
      const eventLine = frame.match(/^event:\s*(.+)$/m)?.[1]?.trim();
      const dataLine  = frame.match(/^data:\s*(.+)$/m)?.[1]?.trim();
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
