# AppBana Documentation

Centralized documentation for the AppBana platform. Every markdown file for humans lives under this folder — nothing else in the repo contains prose documentation.

---

## Folder Layout

| Folder | Purpose |
|--------|---------|
| [`architecture/`](./architecture/) | System design, component architecture, data-binding internals |
| [`features/`](./features/) | Deep dives into shipped features (AI Agent, multi-tenant model, security, templates, adapters) |
| [`guides/`](./guides/) | How-to guides for developers, testers, and end users |
| [`planning/`](./planning/) | Roadmap and forward-looking implementation stories |
| [`specs/`](./specs/) | Feature specifications (auth, workflow) |

Top-level files:

- [`ACTIVE_TASKS.md`](./ACTIVE_TASKS.md) — current sprint / in-flight work
- [`session_summary.md`](./session_summary.md) — most recent working session notes
- [`openapi-fls.yaml`](./openapi-fls.yaml) — OpenAPI specification for Field-Level Security endpoints

---

## Start Here

1. **New to the codebase?** Read [`architecture/01-ARCHITECTURE.md`](./architecture/01-ARCHITECTURE.md), then [`guides/02-DEVELOPMENT_GUIDE.md`](./guides/02-DEVELOPMENT_GUIDE.md).
2. **Starting a new session?** Follow [`guides/SESSION_RESUME_GUIDE.md`](./guides/SESSION_RESUME_GUIDE.md).
3. **Building on the AI Agent?** Read [`features/ai-agent.md`](./features/ai-agent.md) and [`features/ai-builder-service.md`](./features/ai-builder-service.md).
4. **Working on a specific area?** Jump to the folder below.

---

## Architecture

- [`01-ARCHITECTURE.md`](./architecture/01-ARCHITECTURE.md) — full system architecture reference
- [`ARCHITECTURE_VISUAL_SUMMARY.md`](./architecture/ARCHITECTURE_VISUAL_SUMMARY.md) — diagrams and visual summary
- [`ENTITY_FORM_BINDING_ARCHITECTURE.md`](./architecture/ENTITY_FORM_BINDING_ARCHITECTURE.md) — how forms bind to entities at runtime
- [`datasource-adapters.md`](./architecture/datasource-adapters.md) — universal datasource adapter system

## Features

- [`ai-agent.md`](./features/ai-agent.md) — AI Agent design, Think/Act/Observe loop, tools
- [`ai-builder-service.md`](./features/ai-builder-service.md) — the `ai-builder` microservice
- [`builder-database.md`](./features/builder-database.md) — knowledge base fed to the AI agent
- [`multi-tenant-design.md`](./features/multi-tenant-design.md) — physical table isolation & multi-tenant model
- [`SECURITY_FEATURES.md`](./features/SECURITY_FEATURES.md) — auth, RBAC, FLS, CSRF, rate limiting
- [`KNOWLEDGE_BASE.md`](./features/KNOWLEDGE_BASE.md) — RAG knowledge store
- [`page-templates.md`](./features/page-templates.md) — system page templates

## Guides

- [`02-DEVELOPMENT_GUIDE.md`](./guides/02-DEVELOPMENT_GUIDE.md) — local setup and dev workflow
- [`04-USER_MANUAL.md`](./guides/04-USER_MANUAL.md) — end-user Studio guide
- [`SESSION_RESUME_GUIDE.md`](./guides/SESSION_RESUME_GUIDE.md) — session resume checklist
- [`JAVA21_QUICK_REFERENCE.md`](./guides/JAVA21_QUICK_REFERENCE.md) — Java 21 features used in the codebase
- [`API_VERIFICATION.md`](./guides/API_VERIFICATION.md) — API verification procedures
- [`api-client.md`](./guides/api-client.md) — frontend API client & interceptor system
- [`automation-testing.md`](./guides/automation-testing.md) — end-to-end automation testing notes

## Planning

- [`03-ROADMAP.md`](./planning/03-ROADMAP.md) — product roadmap
- [`AI_AGENT_IMPLEMENTATION_PLAN.md`](./planning/AI_AGENT_IMPLEMENTATION_PLAN.md) — AI agent implementation plan
- [`AI_AGENT_STORIES.md`](./planning/AI_AGENT_STORIES.md) — AI agent user stories
- [`AI_SCHEMA_QUALITY_PLAN.md`](./planning/AI_SCHEMA_QUALITY_PLAN.md) — schema quality improvement plan
- [`IMPLEMENTATION_STORIES.md`](./planning/IMPLEMENTATION_STORIES.md) — implementation story backlog

## Specs

- [`AUTH.md`](./specs/AUTH.md) — authentication & RBAC specification
- [`WORKFLOW.md`](./specs/WORKFLOW.md) — workflow automation specification
