# AppBana — Product Plan & Roadmap

_Last updated: 2025-10-01 (Angular workspace plan deprecated; Studio custom UI path active)_

This document outlines the product vision, strategic roadmap, and feature planning for AppBana. It is intended for product owners and stakeholders to guide development efforts.

> Legacy Note: Previous references to an Angular 21 + Nx workspace have been superseded by the custom Studio (Web Components + TypeScript) implementation. Epics retain identifiers for traceability; descriptions adjusted where applicable.

## 1. Product Vision

To be the dominant application platform for rapidly building secure, complex, and scalable enterprise solutions, with a strategic focus on **Healthcare, Logistics, and HR Management**. We will win by providing an end-to-end, metadata-driven experience that is more cohesive, developer-friendly, and industry-aware than any competitor.

## 2. Guiding Principles

1. **End-to-End Cohesion:** Leverage our unique metadata-driven architecture from database to UI as our primary competitive advantage.
2. **Developer Experience First:** Empower developers with a modern, open, and extensible stack that they love to use.
3. **Vertical-Specific Solutions:** Go beyond generic tools to solve real-world industry problems out of the box.
4. **Uncompromising Security & Compliance:** Build trust by making security, auditing, and compliance core, non-negotiable platform features.

## 3. Strategic Roadmap (Q4 2025)

The roadmap is phased to deliver an enterprise-ready v1 platform by the end of December 2025.

### **October 2025: The Enterprise Foundation**
*Goal: Solidify core enterprise-grade features.*

| Epic | Key Features (Adjusted for Studio) | Business Goal |
| :--- | :--- | :--- |
| **Studio UI Foundation (Phase A → early B)** | - BaseElement + registry + core components (DONE) <br>- Recursive runtime renderer + demo page at /ui/studio (PENDING) <br>- Vitest renderer test (PENDING) <br>- Builder canvas skeleton & local draft (Phase B start) | Establish the UI platform needed to deliver workflows, security/auditing, and future features. |
| **Stateful Workflow Engine (MVP)** | - Definitions, instances, transitions, basic audit hook | Enable process automation foundation (HR & Healthcare). |
| **Advanced Security & Auditing** | - CRUD baseline audit (DONE) <br>- Transition audit extension (pending engine) <br>- Field-Level Security engine + redaction/write enforcement (planned) <br>- Audit export + filters (entity, op, actor, date) | Compliance & trust (HIPAA alignment groundwork). |
| **Foundational Plugin API** | - Component registry contract (seeded) <br>- Plugin loading boundary draft (Phase E design) <br>- Example Signature Pad component (Dec earliest) | Prepare extensibility & ecosystem. |

### **November 2025: Vertical Acceleration (Logistics & HR)**
*Goal: Launch high-impact features for Logistics and HR.*

| Epic | Key Features | Business Goal |
| :--- | :--- | :--- |
| **Logistics & Real-Time Operations** | - PWA features for offline data caching and basic sync<br>- Barcode/QR scanner component<br>- WebSocket support for real-time updates<br>- MQTT DataSource for IoT integration | Capture the Logistics market with tailored features that solve core operational needs. |
| **Reporting & Export Engine** | - Visual report designer for tabular reports<br>- Server-side engine to export reports to CSV/Excel | Fulfill critical requirements for HR and business applications. |
| **Advanced Workflow & Permissions** | - Multi-user approval chains<br>- Relationship-based permissions (e.g., "manager of...") | Unlock complex HR use cases (time-off requests, performance reviews). |
| **Multi-tenant Scoping** | - TenantID propagation in auth/session<br>- Query scoping helpers<br>- Designer preview as role/tenant | Support multi-company deployments and secure data isolation. |

### **December 2025: Healthcare & Platform Leadership**
*Goal: Introduce specialized Healthcare features and establish platform leadership.*

| Epic | Key Features | Business Goal |
| :--- | :--- | :--- |
| **Healthcare Interoperability** | - FHIR connector for standard healthcare data (read-only MVP)<br>- Sample patient intake application template | Become the go-to platform for healthcare applications by solving integration challenges. |
| **Specialized Component Library** | - Patient History Timeline component plugin | Provide out-of-the-box value for healthcare developers and demonstrate plugin power. |
| **Governance & Collaboration** | - Versioning and rollback for application designs<br>- Marketplace concept for plugins and templates | Enhance team productivity and reduce risk for enterprise customers. |
| **Document Store** | - Metadata + object storage adapter<br>- Viewer component for PDFs/images<br>- Audited access | Support document-heavy workflows in all verticals. |
| **Exception Rules & Alerts** | - Rule DSL for events<br>- Email/SMS connectors<br>- Alert audit entries | Enable proactive operations management for logistics. |

## 4. Acceptance Criteria (by phase)

### October 2025 — Enterprise Foundation (MVP)
_Progress markers inserted where partially delivered._
- Workflow Engine: persist workflow instances, support states (draft, submitted, approved, rejected), resume by owner, idempotent transitions, audit trail. (NOT STARTED)
- UI schema supports `workflows` and maps actions to transitions; designer can attach Start/Approve actions. (NOT STARTED)
- Advanced Auditing: server persists audit records for all CRUD (DONE) and workflow transitions (PENDING); export CSV + filters (PENDING). 
- Field-Level Security: enforce on read/write + UI runtime hide/disable (PENDING).
- Plugin API: documented baseline (registry seeded), example component (PENDING), data connector skeleton (PENDING).
- Studio Phase A exit: metadata models, registry, demo, core components, unknown placeholder (DONE); renderer + test + packaging (PENDING).

### November 2025 — Logistics & HR Acceleration (MVP)
- PWA: installable, offline cache for static assets and last-used pages; queue-and-replay of POST/PUT/DELETE; background sync.
- Real-time: WebSocket and MQTT data source types; table refreshes on push; backoff/retry and auth header propagation.
- Barcode/QR Scanner: mobile-friendly component with camera permissions, debounce, and event integration.
- Reporting: visual designer can define columns, groups, totals; server generates CSV/Excel with correct types.
- Workflows: multi-actor approvals with assignment to roles; SLA timers for steps and escalations; relationship-based permissions.

### December 2025 — Healthcare & Platform Leadership (MVP)
- FHIR Connector: configure FHIR base URL + auth; list and fetch R4 resources (Patient, Observation, Encounter) with search params; all access audited.
- Patient History Timeline: renders encounters/observations over time with filters; a11y-compliant; handles 1k+ events smoothly.
- Design Versioning: save with semantic version + notes; diff between versions; rollback; access control on publishing.
- Marketplace: discover and enable first-party plugins (Signature Pad, Barcode, Timeline, FHIR); signed manifests and integrity checks.
- Document Store: upload/view PDFs/images; checksums; audited access.

## 5. Industry-Specific Requirements

To succeed, AppBana must address the unique needs of its target verticals:

### Healthcare
- **Requirements:** HIPAA-compliant auditing, granular field-level security, stateful workflows (for patient intake, etc.), specialized UI components (Patient Timeline), and interoperability (FHIR/HL7).
- **Key Features:** Comprehensive audit trails, Field-Level Security, Workflow Engine, FHIR Connector.
- **Compliance Focus:** HIPAA technical safeguards baseline; SOC 2 Type I readiness; BAA template ready by December.

### Shipping & Logistics
- **Requirements:** Real-time data (vessel tracking, yard operations), offline mobile support (scanning), multi-tenant partitioning (carrier vs terminal), barcode scanning, and exception management.
- **Key Features:** PWA/offline capability, WebSocket/MQTT connectors, barcode scanner component, multi-tenant scoping, document store.
- **Key Flows:** Ocean operations, terminal yard management, bookings & allocations, mobile scanning, customer self-service, ESG & compliance reporting.

### HR Management
- **Requirements:** Multi-step approval workflows (onboarding, time-off requests), report generation, complex permission models ("manager of" relationships), third-party integrations.
- **Key Features:** Advanced workflow engine with multi-actor support, reporting & export engine, relationship-based permissions.

## 6. Competitive Analysis & Positioning

### Competitive Landscape
| Competitor | Strengths | Weaknesses | AppBana's Competitive Angle |
| :--- | :--- | :--- | :--- |
| **OutSystems / Mendix** | Mature, feature-rich, strong in workflow and enterprise integration | Proprietary, expensive, steep learning curve | **Simplicity & Control:** More transparent, modern web stack and a clear separation between backend and UI. Our end-to-end metadata story is a cohesive narrative developers appreciate. |
| **Retool / Appsmith** | Excellent for building internal tools quickly | Primarily for internal tools, less flexible for custom UI/UX | **Full Application Platform:** AppBana builds complete, multi-tenant, customer-facing applications. Our robust backend is an advantage over their "connect-to-existing-API" model. |

### Unique Selling Proposition (USP)
*The only end-to-end, metadata-driven platform that goes from database schema definition to a fully functional, custom enterprise UI on a modern, open-standards stack.* We are not just a UI builder; we are an application accelerator.

## 7. KPIs & Success Metrics (Q4 2025)

- **Time-to-First-App:** < 2 hours from install to a CRUD + workflow + report.
- **Design Productivity:** ≥ 70% of UI built via designer (vs custom code nodes).
- **Performance:** P95 list view < 800ms (cached), P95 report export < 5s for 10k rows.
- **Reliability:** < 1% offline sync conflicts unresolved; WebSocket reconnect success P95 < 3s.
- **Security:** 100% of PHI accesses audited; 0 high-severity vulnerabilities at release.

## 8. Risks & Mitigations (Updated Snapshot)
- **UI Foundation Delay:** Renderer/test tasks slip → compress Phase B. Mitigation: prioritize minimal renderer, defer stylistic niceties.
- **Workflow & Audit Coupling:** Parallel development risk. Mitigation: isolate audit extension interface early.
- **Scope Risk:** December timeline is aggressive. Mitigation: MVP strictness, defer non-essential features (PDF export, DICOM viewer) to Q1.
- **Compliance Risk:** HIPAA misinterpretation. Mitigation: engage compliance advisor; run lightweight gap assessment in October.
- **Performance Risk:** Real-time + offline + workflow complexity. Mitigation: performance budgets, profiling, virtualization of large lists.
- **Plugin Security Risk:** Third-party code. Mitigation: signed manifests, review process, sandboxing, allow-listing sources.

## 9. Pilot & UAT Plan

- **Design Partners:** Recruit 1 per vertical by early October; sign BAAs for healthcare pilot.
- **Use Cases:**
  - Healthcare: Patient Intake with read-only FHIR; PHI auditing review.
  - Logistics: Driver scan + live dashboard with offline queue.
  - HR: Time-off approval workflow + exportable report.
- **UAT:** Success criteria mapped to KPIs; weekly feedback loop; track blocker list.

## 10. Post-Q4 Roadmap Preview (Q1 2026)

Features deferred to Q1 2026 include:
- PDF report rendering
- FHIR write operations and SMART on FHIR launch
- DICOM viewer and advanced medical imaging features
- Real-time collaboration in the designer
- Advanced ETA analytics for logistics

## 11. References & Supporting Documents
- **TODO.md:** Current actionable backlog with phase-linked checkboxes (Studio terminology updated).
- **OCT_2025_EPICS_STORIES.md:** Original epic/story list (Angular references legacy; see header note there).
- **UI_Development_Plan.md:** Detailed Studio development plan (authoritative for phases A–F).
- **.github/COPILOT_GUIDE.md:** Assistant-facing status + immediate tasks.
