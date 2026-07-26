# Enterprise Capabilities Epic — Implementation Plan

**Status:** 📝 Spec approved 2026-07-26 · ⏳ Execution not started
**Owner:** AppBana core team
**Position in master roadmap:** Phase D of the post-Stage-4 forward plan (see [ACTIVE_TASKS.md](../ACTIVE_TASKS.md)). **Last** epic — depends on Phase A (Runtime UX Sprint 2), Phase B (Complex UI Epic), and Phase C (Maker-Checker Epic) all completing. D is the *packaging* on top of a differentiated product; the product itself (differentiator + fundamentals) lands in A + B + C.
**Trigger:** A comparative review against a live enterprise SaaS (a global shipping / logistics platform) exposed the categorical gap between AppBana today and what a mid-market B2B customer actually procures. Complex UI (Phase B) and Maker-Checker (Phase C) deliver a differentiated product — but without enterprise SSO, dashboards, notifications, and a professional-feeling shell, no enterprise IT department signs the contract without a fight. This epic closes the four highest-leverage packaging gaps.

**Why D goes after C (not before):** approvals are what AppBana *sells*, D is how it's *packaged*. Ship the product first, package it second. Prospects will PoC with local auth; they won't PoC with broken approvals. Additionally, D benefits from delay — which OIDC provider, group→role mapping, and branding are all better co-designed with a real prospect's identity team than guessed. Delaying D means D1 lands against real requirements from a real customer.

**Related active plans:**
- [Runtime UX Overhaul Plan](./RUNTIME_UX_OVERHAUL_PLAN.md) — **Phase A**, prerequisite.
- [Complex UI Plan](./COMPLEX_UI_PLAN.md) — **Phase B**, prerequisite. B4 master-detail benefits later from D2 dashboards (drill-down from KPI → list → detail).
- [Maker-Checker Plan](./MAKER_CHECKER_PLAN.md) — **Phase C**, prerequisite. C5 ships inside C with a simple polling badge; when D3 lands, C5 gets swapped for the durable rule-driven notification substrate (2–3 hr of throwaway code paid back).
- [AI-Native UI Rebuild Plan](./AI_NATIVE_UI_REBUILD_PLAN.md) — the master rebuild plan; this epic is a post-Stage-4 extension.
- Live status: [`ACTIVE_TASKS.md`](../ACTIVE_TASKS.md).

---

## Table of Contents

1. [TL;DR](#tldr)
2. [Why we are doing this now](#why-we-are-doing-this-now)
3. [Non-goals](#non-goals)
4. [Metadata contract additions](#metadata-contract-additions)
5. [Sub-phase D1 — Enterprise SSO (OIDC + Azure B2C)](#sub-phase-d1--enterprise-sso-oidc--azure-b2c)
6. [Sub-phase D2 — Dashboards + widget primitives](#sub-phase-d2--dashboards--widget-primitives)
7. [Sub-phase D3 — Notifications system](#sub-phase-d3--notifications-system)
8. [Sub-phase D4 — Enterprise shell (multi-level nav, header actions, branded login)](#sub-phase-d4--enterprise-shell-multi-level-nav-header-actions-branded-login)
9. [Cross-cutting concerns](#cross-cutting-concerns)
10. [AI Builder contribution](#ai-builder-contribution)
11. [File-level change map](#file-level-change-map)

---

## TL;DR

Four independently shippable sub-phases upgrade AppBana from "polished CRUD builder" to "credible enterprise platform". Each sub-phase closes a specific procurement blocker that came out of the enterprise-app comparison.

| # | Sub-phase | Backend deliverable | Runtime/Studio deliverable | AI Builder deliverable | Est. |
|---|---|---|---|---|---|
| **D1** | Enterprise SSO | OIDC library, tenant identity-provider config, JIT user provisioning, group→role mapping, `/auth/oidc/*` endpoints | Branded pre-login page detects SSO tenant, redirects to IdP, handles callback | AI treats SSO as a tenant-config concern, not per-app | ~35 hr |
| **D2** | Dashboards + widgets | `/api/widgets/*` aggregation endpoints (COUNT, SUM, GROUP BY, time-bucket), widget-query DSL | `DashboardNode` page type; 6 widget primitives (KPI card, sparkline, donut, bar/stacked-bar, mini-table, progress); auto-refresh every N sec | `scaffold_app` recognises "dashboard" / "overview" intent, generates dashboard page with 3–6 widgets | ~40 hr |
| **D3** | Notifications | `appbana_notifications` table, notification-trigger rules (insert/update/state-change), SSE push, mark-as-read endpoint | Header bell with unread badge, notifications flyout, "only unread" filter, mark-all-as-read | AI can attach notification rules to entities and state transitions | ~30 hr |
| **D4** | Enterprise shell | Tenant branding endpoint gains hero image + secondary buttons | Multi-level sidebar with groups + icons + counts, header action-slots with badge counts, user-account dropdown, fully branded login | Sidebar structure derives from app metadata (`navigation.groups[]`) | ~20 hr |

**Total scope:** ~125 hours of focused work. D1 and D4 can ship independently and in parallel. D2 depends on nothing. D3 depends on D2 only for the "notifications count" widget (optional). D3 also *replaces* Phase C's C5 polling-badge implementation with a durable rule-driven substrate (2–3 hr swap-out).

**Execution order:** A → B → C → D (any D sub-phase order; D1 + D4 parallelizable, D2 standalone, D3 after D2). Alternative fast-path once D starts: D4 → D2 → D1 → D3 (visual wins first, hard security work last).

---

## Why we are doing this now

The reference comparison — a live global-shipping-industry SaaS with ~10,000 concurrent users — inherently needs all four of these before an IT purchasing committee will even open a POC contract:

1. **Enterprise SSO.** Every mid-market and enterprise buyer has Azure AD / Okta / Google Workspace / Ping. Local email/password auth is a categorical no. This is a gate, not a nice-to-have.
2. **Dashboards.** Every executive-facing screen in enterprise SaaS is a KPI dashboard. Ops teams use lists; leadership uses widgets. Without them, the first stakeholder demo lands flat regardless of how good the CRUD is.
3. **Notifications.** Async workflows across teams need a durable notification system: `#TICKET-1234 has been updated` in a bell menu, mark-as-read, unread counts. Every enterprise app has one. Phase C (maker-checker) ships its notification requirement (C5) with a simple polling badge; D3 upgrades it to a durable rule-driven substrate.
4. **Enterprise shell.** Multi-level nav with groups + icons + user account dropdown + header badges is what makes an app feel "real" to a stakeholder in the first 5 seconds. Phase A polishes the *content* area; D4 polishes the *chrome*.

The AI Builder currently generates none of these. The runtime cannot render any of them. Fixing all four at the platform level means every future app the agent scaffolds inherits enterprise-grade capabilities for free.

**Reference use case** (used throughout this doc): a multi-region equipment-tracking application with:
- Azure B2C login with company-branded card and SSO-only entry.
- Dashboard with 8 KPI cards (some with sparklines and trend %), 2 mini-tables, 1 donut, 1 stacked bar chart, 1 auto-refresh timer.
- 3-level sidebar with icons per item, group headers, expand/collapse, user avatar dropdown at top.
- Header actions: integration-health popover (15 badge), user-directory popover (1 badge), notifications bell (4 badge).
- Notifications flyout with "only unread" toggle, `#TICKET-XXX has been updated` lines, timestamps, mark-all-as-read.

Every gap in that description maps to exactly one of D1–D4.

---

## Non-goals

- **Multi-factor authentication as a first-class feature.** MFA is enforced upstream by the IdP (Azure B2C policy, Okta rules). We accept whatever the IdP asserts.
- **Custom widget authoring.** D2 ships 6 opinionated widget types. Custom widgets (user code) deferred to a Phase D.5.
- **Real-time collaborative dashboards** (multiple users editing the same dashboard). Read-only dashboards only.
- **Dashboard drill-through to arbitrary queries.** A widget can click-through to a preconfigured list page filtered by the widget's dimension. No ad-hoc pivot.
- **Push notifications to mobile / email / SMS.** D3 v1 is in-app only. Email routing is D3.5 (deferred).
- **Notification digest / batching.** Every trigger fires one notification. Batching to hourly/daily digests is deferred.
- **Custom theme editor in the Studio.** D4 v1 reads tenant branding from the backend; a Studio-side editor is deferred.
- **Right-to-left languages / i18n.** Out of scope for v1.
- **On-premise SSO with LDAP / Active Directory.** OIDC only in v1. Full SAML support deferred.

---

## Metadata contract additions

All four sub-phases add optional fields to existing types in [`app-bana-shared/src/metadata.ts`](../../app-bana-shared/src/metadata.ts). **Backward compatible** — existing apps continue to render unchanged.

```ts
// AppMeta additions
export interface AppMeta {
  // ...existing fields
  navigation?: NavigationConfig;       // D4 — multi-level sidebar structure
  headerActions?: HeaderActionSlot[];  // D4 — bell / user / integrations popovers
}

export interface NavigationConfig {
  groups: NavigationGroup[];
}
export interface NavigationGroup {
  id: string;
  label: string;
  icon?: string;                       // lucide-react icon name
  items: NavigationItem[];
  defaultOpen?: boolean;
}
export interface NavigationItem {
  id: string;
  label: string;
  icon?: string;
  pageId: string;                      // links to PageMeta.id
  badgeSource?: WidgetQuery;           // optional live count in nav
}

export interface HeaderActionSlot {
  id: string;
  icon: string;
  kind: 'popover' | 'flyout';
  badgeSource?: WidgetQuery;           // e.g. unread notification count
  contentPageId?: string;              // page rendered inside the popover
}

// PageMeta additions
export interface PageMeta {
  // ...existing fields
  layout?: 'form' | 'list' | 'wizard' | 'master-detail' | 'dashboard';   // D2 adds 'dashboard'
  dashboard?: DashboardConfig;         // D2
  refreshIntervalMs?: number;          // D2 — auto-refresh (0 = off)
}

export interface DashboardConfig {
  grid: WidgetPlacement[];
  columns: number;                     // 12-col grid default
}
export interface WidgetPlacement {
  widgetId: string;
  col: number;
  row: number;
  colSpan: number;
  rowSpan: number;
}
export interface WidgetMeta {
  id: string;
  type: 'kpi' | 'sparkline' | 'donut' | 'bar' | 'stacked-bar' | 'mini-table' | 'progress';
  title: string;
  query: WidgetQuery;
  format?: WidgetFormat;
  drillToPageId?: string;              // click-through target
}
export interface WidgetQuery {
  entity: string;                      // fully qualified {tenantId}_{appId}_{entity}
  aggregate: 'count' | 'sum' | 'avg' | 'min' | 'max';
  aggregateField?: string;             // required for sum/avg/min/max
  groupBy?: string;                    // dimension for donut / bar
  timeBucket?: 'day' | 'week' | 'month'; // for sparklines
  filter?: Record<string, unknown>;    // where clause
  compareWindow?: 'prev_period';       // enables trend arrow + %
  limit?: number;                      // mini-table row cap
}
export interface WidgetFormat {
  prefix?: string;                     // '$'
  suffix?: string;                     // 'h', '%', 'kg'
  decimals?: number;
  trendDirection?: 'higher-is-better' | 'lower-is-better';
}

// TenantBranding additions (D4)
export interface TenantBranding {
  // ...existing fields (logoUrl, primaryColor, appName)
  heroImageUrl?: string;               // login screen background
  loginCard?: LoginCardConfig;
  ssoProviders?: SsoProviderRef[];     // D1
}
export interface LoginCardConfig {
  title?: string;                      // "Sign in with your email address"
  backgroundColor?: string;            // dark card overlay
  buttonPrimaryLabel?: string;
  buttonSecondaryLabel?: string;       // "WalWil Sign in" -> SSO
}
export interface SsoProviderRef {
  id: string;                          // 'azure-b2c-main'
  displayName: string;                 // "Sign in with Wallenius SSO"
  kind: 'oidc';                        // v1 only supports OIDC (Azure B2C is OIDC-compliant)
}

// New — Notification schema (D3, backend-owned but exported for UI typing)
export interface NotificationRule {
  id: string;
  entity: string;
  triggers: ('insert' | 'update' | 'delete' | 'state-change')[];
  stateChangeFrom?: string;
  stateChangeTo?: string;
  messageTemplate: string;             // "#{id} {entity} has been {trigger}"
  audienceRule: AudienceRule;
}
export interface AudienceRule {
  kind: 'all-users' | 'role' | 'entity-owner' | 'entity-field';
  role?: string;
  fieldName?: string;                  // if 'entity-field', notify user whose id matches this field
}
```

---

## Sub-phase D1 — Enterprise SSO (OIDC + Azure B2C)

**Est: ~35 hr · Owner: backend + shared package · Ships: single PR.**

### What it does
Adds tenant-scoped OIDC identity-provider configuration. A tenant with `ssoProviders[]` configured presents an SSO button on its login card; local email/password remains available unless the tenant marks the provider as `enforced: true`. On successful IdP callback, AppBana issues its own session token (unchanged downstream), JIT-provisions the user if new, and maps IdP group claims → AppBana roles.

### Backend

**New Liquibase changeset** `db.changelog-v13-sso.xml`:
```sql
CREATE TABLE appbana_sso_providers (
  id                    VARCHAR(64) PRIMARY KEY,
  tenant_id             VARCHAR(64) NOT NULL REFERENCES appbana_tenants(id),
  display_name          VARCHAR(255) NOT NULL,
  kind                  VARCHAR(16) NOT NULL,   -- 'oidc'
  issuer_url            VARCHAR(500) NOT NULL,  -- e.g. https://walwilb2c.b2clogin.com/…/v2.0
  client_id             VARCHAR(255) NOT NULL,
  client_secret_enc     VARCHAR(1000) NOT NULL, -- encrypted at rest
  redirect_uri          VARCHAR(500) NOT NULL,
  scopes                VARCHAR(500) NOT NULL DEFAULT 'openid profile email',
  group_claim           VARCHAR(64),            -- claim name that holds group ids
  group_role_map        JSONB,                  -- {"admins": "admin", "operators": "editor"}
  enforced              BOOLEAN NOT NULL DEFAULT FALSE,
  created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE appbana_users
  ADD COLUMN sso_subject VARCHAR(255),                    -- IdP-issued subject
  ADD COLUMN sso_provider_id VARCHAR(64) REFERENCES appbana_sso_providers(id);
CREATE UNIQUE INDEX ux_users_sso ON appbana_users(sso_provider_id, sso_subject)
  WHERE sso_subject IS NOT NULL;
```

**New files:**
- `app-bana-service/src/main/java/com/appbana/auth/oidc/OidcClient.java` — thin wrapper over `pac4j` or hand-rolled JWKS verifier. Handles authorization-code + PKCE flow.
- `app-bana-service/src/main/java/com/appbana/auth/oidc/SsoProviderService.java` — CRUD for `appbana_sso_providers`, secret encryption via existing `com.appbana.security.CryptoService`.
- `app-bana-service/src/main/java/com/appbana/auth/oidc/JitProvisioner.java` — creates user on first login, maps `group_claim` → role.
- `app-bana-service/src/main/java/com/appbana/server/routes/OidcRoutes.java` — new routes.

**New endpoints** (registered in `ApiServer.java`):
```
GET  /auth/oidc/{providerId}/authorize          -> 302 to IdP
GET  /auth/oidc/{providerId}/callback           -> 302 to app with session cookie set
POST /admin/tenants/{tenantId}/sso-providers    -> tenant-admin CRUD (secret write-only)
GET  /admin/tenants/{tenantId}/sso-providers    -> list (no secrets in response)
DELETE /admin/tenants/{tenantId}/sso-providers/{id}
```

**Existing endpoints extended:**
- `GET /api/tenants/{tenantId}/branding` — response now includes `ssoProviders[]` (display data only, never client secrets).
- `POST /api/auth/login` — if tenant has an enforced provider and this call receives an email/password, respond `403 sso_required` with the provider list.

**Dependencies:**
- Add `com.nimbusds:oauth2-oidc-sdk:11.x` to [`app-bana-service/pom.xml`](../../app-bana-service/pom.xml) (Apache-licensed, MIT-quality maintained). Rules out `pac4j` (heavier, doesn't play well with the current handler style).

### Frontend

**Shared package:** [`app-bana-shared/src/api-client.ts`](../../app-bana-shared/src/api-client.ts) — add `beginOidcLogin(providerId)` that navigates to `/auth/oidc/{id}/authorize`.

**Studio + Runtime:** both use the same [`AuthGate.tsx`](../../app-bana-studio/src/features/auth/AuthGate.tsx) pattern (Runtime has its own copy). On mount, fetch tenant branding, render:
- Local email/password form if no SSO providers configured OR provider not enforced.
- One button per SSO provider under the local form.
- Suppress the local form entirely if `provider.enforced === true`.

Callback route: the runtime doesn't need a JS route — the IdP redirects to the backend `/auth/oidc/{id}/callback`, which sets a session cookie and 302-redirects to the app root. On landing, the existing session-hydrate flow picks it up.

### Exit criteria
- [ ] A tenant admin can register an Azure B2C provider via API.
- [ ] The login screen renders the "Sign in with SSO" button when a provider exists.
- [ ] End-to-end round trip: click SSO button → IdP login → callback → app loads with correct user.
- [ ] JIT-provisioned users get roles from group claims.
- [ ] `enforced: true` disables local login entirely for that tenant.
- [ ] Existing tenants without an SSO provider are unaffected (no regressions).
- [ ] Unit tests cover JWT validation, replay-attack rejection, expired-token rejection.

---

## Sub-phase D2 — Dashboards + widget primitives

**Est: ~40 hr · Owner: backend + runtime · Ships: single PR (widgets ship as a set, not one at a time).**

### What it does
Adds a new `layout: 'dashboard'` page type. A dashboard is a grid of widgets, each backed by an aggregation query against a single entity. Widgets auto-refresh at a configurable interval and can click-through to a filtered list page.

### Backend

**New file:** `app-bana-service/src/main/java/com/appbana/server/routes/WidgetRoutes.java`.

**New endpoint:**
```
POST /api/widgets/query
Body: WidgetQuery (see metadata contract)
Response: { value: number | Array<{label, value, previous?}> | Row[], comparedTo?: number }
```

The endpoint validates the entity name follows the `{tenantId}_{appId}_{entity}` protocol, then routes to `SchemaManager` to resolve the physical table and issue a parameterised SQL query. For safety, only whitelisted aggregation functions and only entity-schema-declared field names are accepted (no raw SQL).

**Aggregation types supported (v1):**
- `count()`, `count(distinct field)`, `sum(field)`, `avg(field)`, `min(field)`, `max(field)`
- Group-by on any indexed field.
- Time-bucketing (`day` / `week` / `month`) on any `date` / `datetime` field.
- `compareWindow: 'prev_period'` — issues a second query for the previous equivalent window, response includes `comparedTo`.

**Performance:**
- All widget queries share a **60-second in-process cache** keyed on the serialised query (dev-mode configurable).
- Widget queries are queued through the existing virtual-thread executor; a per-request cap of 12 parallel queries prevents dashboard-load stampede.

### Frontend (runtime)

**New files (all under `app-bana-runtime/src/runtime/dashboard/`):**
- `DashboardRenderer.tsx` — reads `PageMeta.dashboard`, lays out 12-col grid using CSS grid, mounts widgets in placement order.
- `useWidgetQuery.ts` — hook. Calls `POST /api/widgets/query`, handles loading/error, wires the page-level `refreshIntervalMs` for auto-refresh.
- `widgets/KpiCard.tsx` — big number + optional trend arrow + optional sparkline underneath.
- `widgets/Sparkline.tsx` — tiny inline chart, 30-point line, no axes.
- `widgets/DonutChart.tsx` — recharts donut with center label.
- `widgets/BarChart.tsx` — vertical bars, single or stacked series.
- `widgets/MiniTable.tsx` — top-N rows in a compact table.
- `widgets/ProgressBar.tsx` — single-metric progress bar with sub-metric splits.

**Dependency:** add `recharts` to `app-bana-runtime/package.json` (Apache-2.0). Not `chart.js` — recharts composes better with React Suspense and our existing Tailwind theming.

**Renderer wiring:** [`Renderer.tsx`](../../app-bana-runtime/src/runtime/Renderer.tsx) gains a branch: when `PageMeta.layout === 'dashboard'`, render `<DashboardRenderer />` instead of the default form/list.

**Auto-refresh:** page-level `refreshIntervalMs`. Widget queries pause when the browser tab is hidden (`document.visibilityState`).

### AI Builder

**Change to `ScaffoldAppTool.java` + `GeneratePageTool.java`:**
- Prompt gains a hint: *"If the app has any operational overview or admin summary use case, generate a dashboard page named 'Dashboard' with 3–6 widgets over the primary entities."*
- New `layout: 'dashboard'` accepted in tool schema.
- Agent selects widget types based on entity fields it created: any entity with a `status` field gets a donut; any with a `created_at` field gets a sparkline over inserts-per-day; any with a `decimal` amount field gets a KPI card with sum + trend.

### Exit criteria
- [ ] The 6 widget primitives render correctly against dev data.
- [ ] `scaffold_app` on a request like "spice-selling app with a dashboard" generates a dashboard with sensible widgets.
- [ ] Auto-refresh works and pauses on tab hide.
- [ ] Widget query caching hits ≥90 % on dashboard reload within 60 s.
- [ ] Click-through from widget → filtered list works.
- [ ] Widget queries reject any entity not in the caller's `{tenantId}_{appId}_*` namespace.

---

## Sub-phase D3 — Notifications system

**Est: ~30 hr · Owner: backend + runtime · Ships: single PR.**

### What it does
Adds a durable, per-user notifications table. Every entity mutation can trigger a notification via a declarative rule (defined at scaffold time by the AI Builder or later by an admin). Notifications appear in a header bell flyout with unread badge, mark-as-read, and "only unread" filter. Delivery is SSE-pushed in real time and read-back on session start.

### Backend

**New Liquibase changeset** `db.changelog-v14-notifications.xml`:
```sql
CREATE TABLE appbana_notification_rules (
  id                VARCHAR(64) PRIMARY KEY,
  tenant_id         VARCHAR(64) NOT NULL,
  app_id            VARCHAR(64) NOT NULL,
  entity_name       VARCHAR(255) NOT NULL,
  triggers          VARCHAR(255) NOT NULL,       -- 'insert,update,state-change'
  state_from        VARCHAR(64),
  state_to          VARCHAR(64),
  message_template  VARCHAR(1000) NOT NULL,
  audience_rule     JSONB NOT NULL,
  created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE appbana_notifications (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id         VARCHAR(64) NOT NULL,
  user_id           VARCHAR(64) NOT NULL,
  rule_id           VARCHAR(64) REFERENCES appbana_notification_rules(id),
  entity_name       VARCHAR(255),
  entity_row_id     VARCHAR(255),
  message           VARCHAR(1000) NOT NULL,
  read_at           TIMESTAMP,
  created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_notif_user_unread ON appbana_notifications(user_id, read_at) WHERE read_at IS NULL;
CREATE INDEX ix_notif_user_recent ON appbana_notifications(user_id, created_at DESC);
```

**New files:**
- `app-bana-service/src/main/java/com/appbana/notifications/NotificationRuleService.java` — CRUD for rules.
- `app-bana-service/src/main/java/com/appbana/notifications/NotificationDispatcher.java` — evaluates rules on entity mutations, expands audience, inserts rows, publishes SSE.
- `app-bana-service/src/main/java/com/appbana/notifications/NotificationSseHub.java` — per-user SSE emitter registry (mirrors the existing `AgentStreamController` pattern).
- `app-bana-service/src/main/java/com/appbana/server/routes/NotificationRoutes.java` — user-facing routes.

**Hook point:** [`GenericEntityRoutes.java`](../../app-bana-service/src/main/java/com/appbana/server/routes/GenericEntityRoutes.java) inserts a post-write hook that calls `NotificationDispatcher.onEntityMutation(...)` after every `INSERT`/`UPDATE`. Backwards compatible — dispatcher no-ops if no rules match.

**New endpoints:**
```
GET  /api/notifications?onlyUnread=bool&limit=50&offset=0
POST /api/notifications/{id}/read
POST /api/notifications/read-all
GET  /api/notifications/stream                              -- SSE, per-session
POST /admin/apps/{appId}/notification-rules                -- CRUD
GET  /admin/apps/{appId}/notification-rules
DELETE /admin/apps/{appId}/notification-rules/{ruleId}
```

### Frontend

**New files (studio + runtime both consume; the primitive lives in `app-bana-shared` and each app imports it):**
- `app-bana-shared/src/notifications-client.ts` — `listNotifications()`, `markRead()`, `openStream()` (returns an `EventSource`).
- `app-bana-runtime/src/runtime/notifications/NotificationsBell.tsx` — bell icon with unread count badge, click opens flyout.
- `app-bana-runtime/src/runtime/notifications/NotificationsFlyout.tsx` — right-side sliding panel, list of items, "Only unread" toggle, "Mark all as read" action.
- `app-bana-runtime/src/runtime/notifications/useNotifications.ts` — hook wrapping list + SSE stream + local state.

**Shell integration:** the bell mounts as a `HeaderActionSlot` (see D4). The unread count comes from the SSE stream (real-time increment) and reconciles on tab focus.

### AI Builder

**Change to `ScaffoldAppTool.java`:**
- If the app is a **ticketing / request / approval** domain, generate a default notification rule: on any `state-change` from `open` → `assigned` → `resolved`, notify the entity's `assignee_id` user.
- If the app has an approval workflow (Phase C), C5 will register rules automatically. This epic just provides the substrate.

**New tool (optional, defer if tight on time):** `create_notification_rule` — first-class tool for the agent to add rules mid-conversation.

### Exit criteria
- [ ] Creating a row in a triggered entity fires a notification within 500 ms end-to-end.
- [ ] Bell shows correct unread count on session start.
- [ ] SSE reconnect works after backend restart.
- [ ] Mark-as-read + mark-all-as-read persist across sessions.
- [ ] Notifications survive backend restart (durable in DB).
- [ ] `NotificationDispatcher` overhead adds <5 ms to a normal entity insert when no rules match.

---

## Sub-phase D4 — Enterprise shell (multi-level nav, header actions, branded login)

**Est: ~20 hr · Owner: runtime + shared package · Ships: single PR.**

**Prerequisite (added 2026-07-26):** D4 assumes the `TenantBranding.primaryColor` → CSS-variable wiring is already in place from [Phase A2 Sprint 3.9](./RUNTIME_UX_OVERHAUL_PLAN.md#sprint-3--runtime-foundations) — i.e. the primary-color audit is complete and `.appbana-button` / active-nav / focus rings already switch color when a tenant's `primaryColor` changes. D4 does **not** re-do that plumbing; D4 adds the hero image, secondary buttons, multi-level navigation shell, and header action slots *on top of* that foundation. If Sprint 3.9 is not shipped when D4 starts, D4 is blocked — do not duplicate the branding-variable wiring in D4.

### What it does
Upgrades the runtime chrome from "flat sidebar + no header" to "grouped multi-level sidebar with icons + user dropdown + configurable header action slots". Also finishes the branded login screen introduced in Stage 0 but never fully wired (adds hero image + secondary-CTA buttons; A2 §3.9 already delivered the tenant `primaryColor` on the primary CTA).

### Frontend

**Rewrite:** [`app-bana-runtime/src/runtime/RuntimeSidebar.tsx`](../../app-bana-runtime/src/runtime/RuntimeSidebar.tsx) — replaces the current flat list with grouped rendering driven by `AppMeta.navigation`. Backwards compat: if `AppMeta.navigation` is absent, fall back to the current flat page list.
- Collapsed / expanded groups with local-storage-persisted state.
- Icon per item using `lucide-react` (already available via `shadcn/ui`).
- Optional live badge count per item (uses D2 widget-query endpoint).
- User avatar + dropdown at top of sidebar with:
  - Current user name + email.
  - "Sign out" action.
  - "Switch tenant" (if multi-tenant user).

**New files:**
- `app-bana-runtime/src/runtime/shell/RuntimeHeader.tsx` — thin top bar. Renders `AppMeta.headerActions[]` in order.
- `app-bana-runtime/src/runtime/shell/HeaderActionSlot.tsx` — one slot: icon + badge + popover container.
- `app-bana-runtime/src/runtime/shell/UserMenu.tsx` — extracted user dropdown (used both in sidebar and header).

**Login screen rewrite:** [`AuthGate.tsx`](../../app-bana-studio/src/features/auth/AuthGate.tsx) and its runtime twin gain:
- Full-screen hero image from `TenantBranding.heroImageUrl`.
- Dark card overlay per `TenantBranding.loginCard.backgroundColor`.
- Custom card title, button labels.
- SSO buttons rendered below or instead of local form (see D1).

### Backend

**Extend** `TenantBrandingRoutes` (already exists from Stage 0) to serve the new `heroImageUrl`, `loginCard`, and — after D1 — `ssoProviders[]`.

**New Liquibase changeset** `db.changelog-v15-tenant-branding.xml`:
```sql
ALTER TABLE appbana_tenants
  ADD COLUMN hero_image_url        VARCHAR(500),
  ADD COLUMN login_card_bg         VARCHAR(32),
  ADD COLUMN login_card_title      VARCHAR(255),
  ADD COLUMN login_btn_primary     VARCHAR(64),
  ADD COLUMN login_btn_secondary   VARCHAR(64);
```

### AI Builder

**Change to `ScaffoldAppTool.java` + `GeneratePageTool.java`:**
- After creating entities, agent groups them by domain (e.g. `Customer, Contact, Address` → group "Customers") and emits a `navigation` block on `AppMeta`.
- Heuristic: entities that reference each other via FK cluster into the same group.
- If the agent creates a dashboard page, it becomes the first sidebar item (no group).

### Exit criteria
- [ ] Sidebar renders grouped navigation with icons and expand/collapse.
- [ ] User dropdown menu works (sign out, view profile).
- [ ] Header action slots render with badges.
- [ ] Notifications bell (from D3) mounts as a header slot.
- [ ] Branded login screen renders per-tenant hero image + colors.
- [ ] Backwards compat: an app with no `navigation` metadata renders exactly as it does today.

---

## Cross-cutting concerns

### Security
- **D1** — Every OIDC-related endpoint is CSRF-exempt (state parameter provides the equivalent guarantee). Client secrets are encrypted at rest using existing `CryptoService`. `redirect_uri` is validated against a whitelist per provider. `state` and `nonce` are single-use and 5-minute-expiring.
- **D2** — Widget queries reject any entity outside `{tenantId}_{appId}_*` namespace. No raw SQL accepted. Aggregation function whitelist enforced.
- **D3** — Users can only mark their own notifications as read. Rules are scoped per app; only tenant admins can CRUD rules.
- **D4** — Tenant branding is public (pre-login); no PII surfaced. Sign-out clears session and revokes any active SSE streams.

### Backwards compatibility
Every sub-phase is additive. Existing apps, tenants, and users continue to work without change:
- App with no `AppMeta.navigation` → flat sidebar as today.
- App with no dashboard page → renders form/list pages as today.
- Tenant with no SSO providers → email/password only.
- No notification rules → no notifications, no overhead.

### Test coverage bar
- **D1:** unit tests for JWT verification, JIT provisioning, group→role mapping, state/nonce replay rejection. Integration test with a mock OIDC provider (`mockoon` or `oidc-provider` npm package running in-process).
- **D2:** unit tests for each widget query aggregation, cache hit/miss behaviour. E2E test that a scaffolded dashboard loads and shows non-zero widget values.
- **D3:** unit tests for rule matching + audience expansion. Integration test end-to-end (insert row → notification appears via SSE).
- **D4:** snapshot tests for sidebar rendering (with and without navigation metadata). Visual regression test for branded login.

### Performance targets
- **D2:** dashboard first-paint under 800 ms on a 6-widget page against a 10 k-row dataset. Achieved via the 60-second widget cache + parallel query dispatch.
- **D3:** notification insert path adds <5 ms to a normal entity write on a rule-less entity. Dispatcher does one indexed rule lookup before deciding to no-op.
- **D4:** sidebar collapse/expand does not re-fetch any data — local UI state only.

---

## AI Builder contribution

Each sub-phase carries a specific AI Builder change so the agent can produce these primitives from a natural-language prompt without any hand-editing:

| Sub-phase | Agent prompt hint | New tool call shape |
|---|---|---|
| D1 | "SSO is a tenant-admin configuration concern, not per-app. Do not ask users about SSO during app scaffolding." | (none — SSO is provisioned separately) |
| D2 | "For any operational or admin app, generate a Dashboard page as the first page. Pick 3–6 widgets from `kpi \| donut \| bar \| sparkline \| mini-table \| progress` based on entity fields." | `generate_page(layout='dashboard', widgets=[...])` |
| D3 | "For any ticketing / request / assignment domain, register a notification rule on state changes to the assignee." | `create_notification_rule(entity, triggers, audience)` |
| D4 | "Group related entities (via FK references) into one navigation group. Emit `navigation.groups[]` on the final `AppMeta`." | `create_app` accepts an optional `navigation` block. |

All four changes are **additive** to existing tool schemas — no breaking changes to the agent's tool interface.

---

## File-level change map

### New files (create)

| File | Sub-phase | Purpose |
|---|---|---|
| `app-bana-service/src/main/resources/db/changelog/db.changelog-v13-sso.xml` | D1 | SSO provider table + user SSO columns |
| `app-bana-service/src/main/java/com/appbana/auth/oidc/OidcClient.java` | D1 | OIDC authorization-code + PKCE client |
| `app-bana-service/src/main/java/com/appbana/auth/oidc/SsoProviderService.java` | D1 | Tenant SSO CRUD |
| `app-bana-service/src/main/java/com/appbana/auth/oidc/JitProvisioner.java` | D1 | JIT user provisioning + role mapping |
| `app-bana-service/src/main/java/com/appbana/server/routes/OidcRoutes.java` | D1 | `/auth/oidc/*` + admin routes |
| `app-bana-service/src/main/java/com/appbana/server/routes/WidgetRoutes.java` | D2 | `/api/widgets/query` |
| `app-bana-service/src/main/resources/db/changelog/db.changelog-v14-notifications.xml` | D3 | Notification rules + notifications tables |
| `app-bana-service/src/main/java/com/appbana/notifications/NotificationRuleService.java` | D3 | Rule CRUD |
| `app-bana-service/src/main/java/com/appbana/notifications/NotificationDispatcher.java` | D3 | Trigger evaluation + audience expansion |
| `app-bana-service/src/main/java/com/appbana/notifications/NotificationSseHub.java` | D3 | Per-user SSE emitter registry |
| `app-bana-service/src/main/java/com/appbana/server/routes/NotificationRoutes.java` | D3 | User + admin notification routes |
| `app-bana-service/src/main/resources/db/changelog/db.changelog-v15-tenant-branding.xml` | D4 | Branding columns on tenants |
| `app-bana-runtime/src/runtime/dashboard/DashboardRenderer.tsx` | D2 | 12-col dashboard grid |
| `app-bana-runtime/src/runtime/dashboard/useWidgetQuery.ts` | D2 | Widget query hook + auto-refresh |
| `app-bana-runtime/src/runtime/dashboard/widgets/KpiCard.tsx` | D2 | KPI widget |
| `app-bana-runtime/src/runtime/dashboard/widgets/Sparkline.tsx` | D2 | Sparkline widget |
| `app-bana-runtime/src/runtime/dashboard/widgets/DonutChart.tsx` | D2 | Donut widget |
| `app-bana-runtime/src/runtime/dashboard/widgets/BarChart.tsx` | D2 | Bar / stacked-bar widget |
| `app-bana-runtime/src/runtime/dashboard/widgets/MiniTable.tsx` | D2 | Mini-table widget |
| `app-bana-runtime/src/runtime/dashboard/widgets/ProgressBar.tsx` | D2 | Progress bar widget |
| `app-bana-runtime/src/runtime/notifications/NotificationsBell.tsx` | D3 | Header bell + badge |
| `app-bana-runtime/src/runtime/notifications/NotificationsFlyout.tsx` | D3 | Slide-in flyout |
| `app-bana-runtime/src/runtime/notifications/useNotifications.ts` | D3 | List + SSE hook |
| `app-bana-runtime/src/runtime/shell/RuntimeHeader.tsx` | D4 | Top bar |
| `app-bana-runtime/src/runtime/shell/HeaderActionSlot.tsx` | D4 | One header action |
| `app-bana-runtime/src/runtime/shell/UserMenu.tsx` | D4 | Extracted user dropdown |
| `app-bana-shared/src/notifications-client.ts` | D3 | Shared notification API client |

### Modified files

| File | Sub-phases | Change |
|---|---|---|
| `app-bana-shared/src/metadata.ts` | D1, D2, D3, D4 | Add types listed under [Metadata contract additions](#metadata-contract-additions) |
| `app-bana-shared/src/api-client.ts` | D1, D2, D3 | Add `beginOidcLogin`, `queryWidget`, notification helpers |
| `app-bana-service/pom.xml` | D1 | Add `com.nimbusds:oauth2-oidc-sdk` dependency |
| `app-bana-runtime/package.json` | D2 | Add `recharts` |
| `app-bana-service/src/main/java/com/appbana/ApiServer.java` | D1, D2, D3 | Register new route classes |
| `app-bana-service/src/main/java/com/appbana/server/routes/GenericEntityRoutes.java` | D3 | Add post-mutation hook to `NotificationDispatcher` |
| `app-bana-service/src/main/java/com/appbana/server/routes/TenantBrandingRoutes.java` | D1, D4 | Serve extended branding (hero, login card, sso providers) |
| `app-bana-service/src/main/java/com/appbana/server/routes/AuthRoutes.java` | D1 | `403 sso_required` when tenant enforces SSO |
| `app-bana-runtime/src/runtime/Renderer.tsx` | D2 | Branch on `layout === 'dashboard'` |
| `app-bana-runtime/src/runtime/AppRuntimeShell.tsx` | D4 | Mount `RuntimeHeader` above page slot |
| `app-bana-runtime/src/runtime/RuntimeSidebar.tsx` | D4 | Grouped nav + icons + user menu |
| `app-bana-studio/src/features/auth/AuthGate.tsx` | D1, D4 | Hero image, branded card, SSO buttons |
| `app-bana-runtime/src/runtime/auth/*` (equivalent) | D1, D4 | Same for runtime login |
| `ai-builder/src/main/java/com/appbana/ai/agent/tool/ScaffoldAppTool.java` | D2, D3, D4 | Emit dashboard + navigation + default notification rules |
| `ai-builder/src/main/java/com/appbana/ai/agent/tool/GeneratePageTool.java` | D2 | Accept `layout: 'dashboard'` + widget config |
| `ai-builder/src/main/java/com/appbana/ai/llm/AdvancedPromptEngine.java` | D2, D3, D4 | Add domain-hint blocks (dashboard-first, notification rules, navigation grouping) |
| `.github/copilot-instructions.md` | all | Update §6 with new metadata types; §9 API reference; §11 field types |
| `docs/README.md` | all | Add this plan to navigation |
| `docs/ACTIVE_TASKS.md` | all | Forward-plan table gains Phase D row |

**Total new files:** 27 · **Total modified files:** 15 · **Total Liquibase migrations:** 3
