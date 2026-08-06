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
  // S1.13: a real response always has a non-blank user.tenantId (UserDTO's compact
  // constructor rejects null/blank tenantId server-side) - a missing value here means
  // an unexpected response shape, not a legitimate anonymous/default tenant. Defaulting
  // to 'default' would silently place the caller in someone else's real tenant
  // namespace instead of surfacing the shape mismatch.
  const tenantId = body.user?.tenantId;
  if (!tenantId) throw new Error('Login response missing user.tenantId');
  return {
    token: body.token ?? body.sessionId,
    userId: String(body.user?.id ?? ''),
    email: body.user?.email ?? email,
    name: body.user?.name ?? '',
    tenantId,
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
  // S1.13: see login() above - fail closed rather than default into a real tenant.
  const tenantId = body.user?.tenantId;
  if (!tenantId) throw new Error('Registration response missing user.tenantId');
  return {
    token: body.token ?? body.sessionId,
    userId: String(body.user?.id ?? ''),
    email: body.user?.email ?? email,
    name: body.user?.name ?? name,
    tenantId,
  };
}

// â”€â”€ Branding & Context â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export async function fetchBranding(tenantId: string): Promise<TenantBranding> {
  const res = await authedFetch(`${BACKEND}/api/tenants/${encodeURIComponent(tenantId)}/branding`);
  if (!res.ok) return { tenantId, displayName: 'AppBana', logoUrl: null, primaryColor: '#6163f0' };
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

/**
 * S2.10 — the union of the caller's own-tenant apps plus any app in another tenant they hold a
 * cross-tenant membership grant on (each tagged with `tenantId` and, for the cross-tenant ones,
 * `role`). This is the ONLY app-listing call the Studio switcher should use: unlike {@link
 * listApps}, which is scoped to a single named tenant, this reflects everywhere the signed-in
 * user actually has access. Backed by `GET /api/users/me/apps`, which derives "own tenant" from
 * the caller's verified session rather than a client-supplied tenant id (see
 * `AppMembershipRoutes.handleListMyApps` for the security rationale).
 */
export async function listMyApps(token: string): Promise<AppMeta[]> {
  const res = await authedFetch(`${BACKEND}/api/users/me/apps`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error(`Failed to list my apps: ${res.status}`);
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
  // Review #5 (High A) — EntityCrudService.parseFilters() no longer runs a
  // second, form-encoding-style decode (URLDecoder) on top of the RFC 3986
  // percent-decode the JDK HTTP server already performs on the query string;
  // that second decode was turning a literal '+' (e.g. in a phone number or
  // timezone offset) into a space. URLSearchParams.toString() encodes a space
  // as '+' (application/x-www-form-urlencoded), which the server no longer
  // converts back — replace it with the RFC 3986 escape ('%20') so a filter
  // value containing a space still round-trips correctly.
  const qs = new URLSearchParams(Object.entries(params).map(([k, v]) => [k, String(v)])).toString()
    .replace(/\+/g, '%20');
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
/**
 * Result of a PUT. Approval-required entities can answer with a *revision*
 * rather than an in-place update: editing an APPROVED record creates a new
 * DRAFT row (C2.3) and leaves the original untouched. Callers that ignore
 * `revision` will tell the user "Saved" and then show them unchanged values.
 */
export interface UpdateEntityRowResult {
  readonly updated: number;
  readonly revision?: boolean;
  readonly revisionId?: string;
  readonly parentId?: string;
  readonly approvalStatus?: string;
  readonly approvalRevision?: number;
}

export async function updateEntityRow(
  entityKey: string,
  id: string | number,
  row: Record<string, unknown>,
  token: string
): Promise<UpdateEntityRowResult> {
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

// ── Approvals (maker-checker, Phase C2/C3) ───────────────────────────────────

/**
 * Thrown when the backend returns 409 from an approval transition — i.e. the
 * record is not in a state the requested transition allows.
 *
 * This is a *conflict*, not a permission failure, and callers must treat it
 * differently: a 403 means "you may never do this", a 409 means "someone else
 * got there first, reload and look again". The backend draws the same
 * distinction via `ApprovalConflictException`.
 */
export class ApprovalConflictError extends Error {
  readonly status = 409;
  constructor(message: string) {
    super(message);
    this.name = 'ApprovalConflictError';
  }
}

/** The four states of the approval state machine, as persisted by the backend. */
export type ApprovalStatus = 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED';

/** Identifies a single record in the approval workflow. */
export interface ApprovalTarget {
  readonly tenantId: string;
  readonly appId: string;
  /** Bare entity name (e.g. `Invoice`), NOT the qualified `{tenant}_{app}_{entity}` key. */
  readonly entityName: string;
  readonly rowId: string | number;
}

/** One entry in a record's approval history. Column names are lower-cased by the backend. */
export interface ApprovalAuditEntry {
  readonly id?: string;
  readonly action?: string;
  readonly status?: string;
  readonly revision?: number;
  readonly actor_user_id?: string;
  readonly comments?: string | null;
  readonly created_at?: string | number | null;
  readonly [key: string]: unknown;
}

function approvalBase(t: ApprovalTarget): string {
  return `${BACKEND}/api/tenants/${encodeURIComponent(t.tenantId)}`
    + `/apps/${encodeURIComponent(t.appId)}`
    + `/entities/${encodeURIComponent(t.entityName)}`;
}

function recordBase(t: ApprovalTarget): string {
  return `${approvalBase(t)}/records/${encodeURIComponent(String(t.rowId))}`;
}

/**
 * Normalise an approval failure. 409 becomes {@link ApprovalConflictError} so
 * the UI can offer "reload" rather than "you lack permission"; everything else
 * falls through to the shared handler, which preserves `.status`.
 */
async function throwApprovalError(res: Response, prefix: string): Promise<never> {
  if (res.status === 409) {
    const raw = await res.text().catch(() => '');
    let message = raw;
    try {
      message = (JSON.parse(raw) as { error?: string }).error ?? raw;
    } catch {
      /* keep raw */
    }
    throw new ApprovalConflictError(message || `${prefix}: conflict`);
  }
  return throwEntityError(res, prefix);
}

async function approvalPost(
  url: string,
  body: Record<string, unknown> | null,
  token: string,
  prefix: string
): Promise<Record<string, unknown>> {
  const res = await authedFetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(body ?? {}),
  });
  if (!res.ok) await throwApprovalError(res, prefix);
  return res.json();
}

/** Move a DRAFT (or REJECTED) record into PENDING. Maker action. */
export async function submitForApproval(
  target: ApprovalTarget,
  token: string,
  comments?: string
): Promise<Record<string, unknown>> {
  return approvalPost(
    `${recordBase(target)}/submit`,
    comments ? { comments } : {},
    token,
    'Submit for approval failed'
  );
}

/** Approve a PENDING record. Checker action; the backend enforces separation of duties. */
export async function approveRecord(
  target: ApprovalTarget,
  token: string,
  comments?: string
): Promise<Record<string, unknown>> {
  return approvalPost(
    `${recordBase(target)}/approve`,
    comments ? { comments } : {},
    token,
    'Approve failed'
  );
}

/** Reject a PENDING record back to its maker. `reason` is required by the backend. */
export async function rejectRecord(
  target: ApprovalTarget,
  token: string,
  reason: string
): Promise<Record<string, unknown>> {
  return approvalPost(`${recordBase(target)}/reject`, { reason }, token, 'Reject failed');
}

/**
 * Unwrap a list endpoint that returns `{ count, <key>: [...] }`.
 *
 * ApprovalRoutes wraps both list responses in an envelope rather than returning
 * a bare array. The array form is still accepted so that a future unwrapping of
 * the endpoint does not silently produce empty lists here.
 */
function unwrapList<T>(payload: unknown, key: string): T[] {
  if (Array.isArray(payload)) return payload as T[];
  const inner = (payload as Record<string, unknown> | null)?.[key];
  return Array.isArray(inner) ? (inner as T[]) : [];
}

/**
 * Every PENDING row of an entity, oldest submission first — a review queue is
 * FIFO, so the longest-waiting record is dealt with first. 403 if the caller is
 * not a checker or app owner for this entity — callers that use this to decide
 * whether to *show* a queue should treat 403 as "empty", not as an error.
 *
 * This only ever returns the first page (see `ApprovalService.QUEUE_PAGE_SIZE`,
 * currently 100). A checker with more pending items than that will not see the
 * rest through this call — use {@link fetchPendingApprovalsPage} and its
 * `hasMore` flag to page through the full queue.
 */
export async function fetchPendingApprovals(
  target: Omit<ApprovalTarget, 'rowId'>,
  token: string,
  level: 1 | 2 = 1
): Promise<Array<Record<string, unknown>>> {
  return (await fetchPendingApprovalsPage(target, token, 0, level)).records;
}

/** One page of the pending-approval queue, as returned by `fetchPendingApprovalsPage`. */
export interface PendingApprovalsPage {
  readonly records: Array<Record<string, unknown>>;
  readonly offset: number;
  readonly pageSize: number;
  /** True when this page was full — there is very likely another page behind it. */
  readonly hasMore: boolean;
}

/**
 * A single page of the pending-approval queue, starting at `offset`.
 *
 * C3.10 — the queue used to be a single `LIMIT 500` fetch with no way to see
 * anything past it. The backend now paginates (`QUEUE_PAGE_SIZE`, with
 * `offset`/`pageSize`/`hasMore` in the response) but nothing on the client
 * asked for a second page, so a checker with more than one page pending only
 * ever saw the first. This is the paging door; `CheckerQueuePage` calls it
 * with an increasing offset while `hasMore` is true.
 */
export async function fetchPendingApprovalsPage(
  target: Omit<ApprovalTarget, 'rowId'>,
  token: string,
  offset = 0,
  level: 1 | 2 = 1
): Promise<PendingApprovalsPage> {
  const levelParam = level === 2 ? '&level=2' : '';
  const url = `${approvalBase({ ...target, rowId: '' })}/approvals/pending?offset=${encodeURIComponent(String(offset))}${levelParam}`;
  const res = await authedFetch(url, { headers: { Authorization: `Bearer ${token}` } });
  if (!res.ok) await throwApprovalError(res, 'Fetch pending approvals failed');
  const body = await res.json();
  const records = unwrapList<Record<string, unknown>>(body, 'records');
  const env = body as { offset?: unknown; pageSize?: unknown; hasMore?: unknown };
  return {
    records,
    offset: typeof env.offset === 'number' ? env.offset : offset,
    pageSize: typeof env.pageSize === 'number' ? env.pageSize : records.length,
    hasMore: env.hasMore === true,
  };
}

/**
 * How many records await this caller's review. Asks the backend for a count
 * only: this drives a polling badge, and fetching the full queue every tick
 * just to read its length would be wasteful.
 *
 * Returns 0 rather than throwing on 403 — "you are not a checker here" means a
 * count of zero, and a badge is not the place to report a permission problem.
 */
export async function fetchPendingApprovalCount(
  target: Omit<ApprovalTarget, 'rowId'>,
  token: string,
  level: 1 | 2 = 1
): Promise<number> {
  const levelParam = level === 2 ? '&level=2' : '';
  const url = `${approvalBase({ ...target, rowId: '' })}/approvals/pending?countOnly=true${levelParam}`;
  const res = await authedFetch(url, { headers: { Authorization: `Bearer ${token}` } });
  if (res.status === 403) return 0;
  if (!res.ok) await throwApprovalError(res, 'Fetch pending count failed');
  const body = await res.json();
  const count = (body as { count?: unknown })?.count;
  return typeof count === 'number' ? count : 0;
}

/** A record's approval history, most recent first. */
export async function fetchApprovalAudit(
  target: ApprovalTarget,
  token: string
): Promise<ApprovalAuditEntry[]> {
  const res = await authedFetch(`${recordBase(target)}/approvals/audit`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) await throwApprovalError(res, 'Fetch approval history failed');
  return unwrapList<ApprovalAuditEntry>(await res.json(), 'history');
}

/** What the caller may do with one entity in the maker-checker workflow. */
export interface EntityRoleGrant {
  readonly roles: string[];
  readonly isMaker: boolean;
  readonly isChecker: boolean;
  /** Two-level checker chain — distinct from `isChecker`; never implied by a 'both' grant. */
  readonly isCheckerL2?: boolean;
  /** 1 (default, single checker) or 2 (checker-1 then checker-2). */
  readonly approvalLevels?: number;
}

/**
 * Identity plus per-entity workflow roles for the signed-in user.
 * `entityRoles` is keyed by bare entity name and only populated when an
 * `appId` was supplied — roles are scoped to a single app.
 */
export interface CurrentUser {
  readonly userId: string;
  readonly email?: string;
  readonly name?: string;
  readonly tenantId: string;
  readonly appId?: string;
  readonly isAppOwner?: boolean;
  readonly entityRoles: Record<string, EntityRoleGrant>;
}

/**
 * Task C3.3 — "who am I, and what may I do here?" in one call.
 *
 * Pass the app scope to get `entityRoles`; without it you get identity only.
 * The per-entity alternative (`/api/tenants/../roles`) costs a round trip per
 * entity on every page load and pushes the BOTH-expands-to-maker+checker rule
 * into the client.
 */
export async function fetchCurrentUser(
  token: string,
  scope?: { tenantId?: string; appId?: string }
): Promise<CurrentUser> {
  const qs = new URLSearchParams();
  if (scope?.tenantId) qs.set('tenantId', scope.tenantId);
  if (scope?.appId) qs.set('appId', scope.appId);
  const suffix = qs.toString() ? `?${qs}` : '';

  const res = await authedFetch(`${BACKEND}/api/users/me${suffix}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) await throwEntityError(res, 'Fetch current user failed');
  const raw = (await res.json()) as CurrentUser;
  return { ...raw, entityRoles: raw.entityRoles ?? {} };
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
  // C4.4e Review #12 — a tool inside an already-open (200) stream hit a backend 401: the
  // session token was present but invalid/expired/revoked. Distinct from a normal `done` so
  // callers can trigger the same recovery as the transport-level `appbana:auth:expired` event
  // that `authedFetch` dispatches for an *outer* 401 (see ChatPane.tsx).
  | { event: 'auth_expired';    data: { message: string } }
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
