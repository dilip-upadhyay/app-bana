# Phase E — Backend Integration & Advanced Backlog

**Status:** 📝 Backlog drafted 2026-07-26 · ⏳ No committed order — customer-demand-driven
**Owner:** AppBana core team
**Position in master roadmap:** Post-launch. Every item in A + B + C + D + Stage 5 must ship before any Phase E work begins. See [ACTIVE_TASKS.md](../ACTIVE_TASKS.md).
**Trigger:** Backend audit on 2026-07-26 identified a set of capabilities that are not blocking first-enterprise-customer-live but will be requested within the first few customer engagements. Rather than commit them to a phase, they live here as a lean prioritized backlog. Items get pulled into the active plan when a specific customer or product decision creates demand.

**Related active plans:**
- [AI-Native UI Rebuild Plan §Stage 5](./AI_NATIVE_UI_REBUILD_PLAN.md#stage-5--production-deploy) — production readiness (containerization, Redis, observability, secrets) that **is** committed pre-launch.
- [Complex UI Plan §B3](./COMPLEX_UI_PLAN.md#sub-phase-b3--file-upload--preview) — file-upload backend already ships in B3 with `LocalFilesystemAdapter`. E1 below adds S3 + Azure Blob impls of the same interface.
- [Maker-Checker Plan §C5.4](./MAKER_CHECKER_PLAN.md#sub-phase-c5--notifications-optional-for-v1-launch) — SMTP email adapter already ships in C5.4. E-Email is out of Phase E for that reason.
- [Enterprise Capabilities Plan §D3](./ENTERPRISE_CAPABILITIES_PLAN.md#sub-phase-d3--notifications-system) — SSE broadcaster ships in D3. E-SSE-Extras (WebSocket) is a separate item below.

---

## Why these live in Phase E and not in A/B/C/D

Two rules were applied during the 2026-07-26 audit:

1. **If a phase's file-level change map already scopes the backend work, it stays in that phase.** File upload (B3), email adapter (C5.4), SSE broadcaster (D3), widget cache (D2) — all already scoped.
2. **If a capability is required to *deploy* AppBana to a real environment, it moves into Stage 5.** Containerization, secrets externalization, Redis externalized state, observability — folded into the rescoped [Stage 5 Production Deploy](./AI_NATIVE_UI_REBUILD_PLAN.md#stage-5--production-deploy).

Everything below is a capability that would strengthen the product but is not required to ship the first enterprise customer.

---

## Backlog — ordered by likely customer demand

| # | Item | Rough scope | Est. | Trigger (when to pull in) |
|---|---|---|---|---|
| **E1** | **Cloud storage adapters** — `S3Adapter` + `AzureBlobAdapter` plugging into B3's existing `FileStorageAdapter` interface. Includes signed-URL direct upload (bypass backend for large files), tenant-scoped bucket/container config, `Content-Disposition` handling on GET. | AWS SDK v2 for S3, `com.azure:azure-storage-blob` for Blob. Config-selectable at tenant level. | ~15 hr | First customer asks "we store files in our own S3 bucket" — usually a data-residency / cost concern. |
| **E2** | **Outbound integration framework** — resilience4j-backed HTTP client (retry, circuit breaker, timeout, per-tenant + per-provider rate limit). Tenant-scoped credential storage in `appbana_integration_credentials` with column-level encryption. Declarative adapter template driven by an OpenAPI spec so the AI Builder can wire up "Salesforce Contact" or "Stripe Customer" from a spec URL. | New `com.appbana.integration` package. Depends on Stage 5.3 (secrets) for credential storage backend. | ~15 hr | First customer asks to integrate a specific external system (Salesforce, Stripe, DocuSign, SAP). |
| **E3** | **Async job queue** — durable jobs in a `appbana_jobs` table (`id`, `tenant_id`, `job_type`, `payload`, `status`, `attempts`, `next_run_at`, `result`). Virtual-thread worker pool per node with cooperative claim-and-lock. Retry with exponential backoff. Dead-letter queue for repeated failures. | Poor man's queue in Postgres (no Kafka/RabbitMQ dependency). Sufficient for bulk-import, PDF-generation, long-running LLM calls. | ~15 hr | First "bulk import 50k rows from CSV" ask, or LLM operations that exceed the 30 s HTTP timeout. |
| **E4** | **CSV / Excel import + export** — `POST /api/{entity}/import` (multipart CSV or XLSX → validated → batched insert with per-row error report). `GET /api/{entity}/export?format=csv&filter=...` (streams filtered dataset). Schema-aware type coercion. | Apache POI or fastexcel for XLSX. Uses async job queue (E3) for imports > 1000 rows. | ~10 hr | Every enterprise customer asks for this. Often within the first sales conversation. |
| **E5** | **Postgres FTS search** — full-text index per entity via `tsvector` generated column + GIN index. `SchemaManager` auto-creates the index for entities with `searchable: true`. `?search=` query on `GenericEntityRoutes` upgrades from `LIKE` to `plainto_tsquery`. OpenSearch adapter is a v2 upgrade. | Zero new dependencies — pure Postgres. | ~8 hr | First customer whose dataset exceeds ~50k rows and complains about search latency. |
| **E6** | **GDPR / data residency** — column-level encryption for fields tagged `pii: true` (AES-GCM via `SecretsProvider`-managed key). `POST /api/gdpr/{tenantId}/{userId}/export` returns a signed JSON+ZIP with every row referencing the subject. `POST /api/gdpr/{tenantId}/{userId}/delete` cascades a redaction across all `pii` columns and audit trail. Column-level audit-log redaction for the audit trail itself. | Depends on Stage 5.3 (secrets) for the master key. Retention policy configurable per entity. | ~10 hr | First EU or California customer. Or a compliance-conscious enterprise prospect (banking, insurance, healthcare). |
| **E7** | **WebSocket upgrade path** — for use-cases where SSE keepalive / reconnect is insufficient (bidirectional streams, presence, collaborative editing). Adds a WebSocket connector alongside the SSE broadcaster. Existing D3 SSE hub remains the default. | Jakarta WebSocket API is already available via Tomcat. Additive, not a replacement. | ~8 hr | If we ever add live multi-user editing or presence indicators. Otherwise unnecessary. |
| **E8** | **API versioning** — `/v1/api/...` prefix for every route class, backwards-compat routing table for the unversioned URLs. Response header `X-API-Version`. Deprecation warnings via `Sunset` HTTP header. | Trivial once, but every future breaking change needs a `/v2/` path. | ~6 hr | Only matters after AppBana has customers running production integrations. Wait for a customer to say "we integrated with your API and now you changed it." |

**Total backlog scope:** ~87 hr. **Nothing is committed** — items get pulled into the active plan (usually into their most-related existing plan or a mini-plan) when a customer ask surfaces.

---

## Not in this backlog (deliberate exclusions)

The audit surfaced these too — noted here for completeness, but deliberately not in Phase E because they belong elsewhere:

- **Multipart file upload backend + `FileStorageAdapter` interface** — already in [B3](./COMPLEX_UI_PLAN.md#sub-phase-b3--file-upload--preview). E1 above only adds S3 + Azure Blob adapter *implementations*.
- **SMTP email adapter** — already in [C5.4](./MAKER_CHECKER_PLAN.md#sub-phase-c5--notifications-optional-for-v1-launch).
- **SSE broadcaster (`NotificationSseHub`)** — already in [D3](./ENTERPRISE_CAPABILITIES_PLAN.md#sub-phase-d3--notifications-system).
- **Widget query cache (60 s in-process)** — already in [D2](./ENTERPRISE_CAPABILITIES_PLAN.md#sub-phase-d2--dashboards--widget-primitives).
- **Redis-backed sessions + rate limit** — in [Stage 5.4](./AI_NATIVE_UI_REBUILD_PLAN.md#stage-5--production-deploy).
- **Secrets externalization** (Key Vault / Secrets Manager / env vars) — in [Stage 5.3](./AI_NATIVE_UI_REBUILD_PLAN.md#stage-5--production-deploy).
- **Containerization** (Dockerfile, Compose, ACA/K8s manifests) — in [Stage 5.2](./AI_NATIVE_UI_REBUILD_PLAN.md#stage-5--production-deploy).
- **Structured JSON logs + `/metrics` + deep `/health` + OTel** — in [Stage 5.5](./AI_NATIVE_UI_REBUILD_PLAN.md#stage-5--production-deploy).
- **Backup/restore runbook** — an ops runbook, not a code change. Lives in the ops repo / runbook folder once Stage 5 ships.
- **Multi-region / read replicas** — a Stage-5-plus-plus concern; if we get there, it's a re-scoping of Stage 5, not a Phase E item.
- **Feature flags service** — not needed at v1; a config-file-driven kill switch works. Reconsider when we have >1 customer on the same version.
- **MFA as a first-class feature** — enforced upstream by the IdP (Azure B2C / Okta policy) per D1's design. Never becomes an app-bana-service concern.

---

## Pull-in protocol

When a customer ask (or a strategic decision) triggers a backlog item:

1. Move its row from this doc into the most-related active plan (or promote to a dedicated mini-plan if it's large).
2. Refine the file-level change map — the estimates here are ballpark; a real plan needs the same per-task-with-est-and-owner rigor as B/C/D.
3. Update [`ACTIVE_TASKS.md`](../ACTIVE_TASKS.md) with the new row and any effect on total effort.
4. Cross-link the pull-in from this doc so history remains searchable.

---

*Last updated: 2026-07-26 · Author: AppBana core team · Status: BACKLOG — items ordered by likely demand, nothing committed.*
