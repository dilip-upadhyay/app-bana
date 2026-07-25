# AppBana: The Autonomous Engine for Agentic Software Generation

AppBana is an **AI-first application builder** that bridges the gap between natural language intent and production-grade software. Unlike traditional no-code platforms, AppBana is built around an **Autonomous Agentic Architecture** that acts as your Expert Architect, Data Modeler, and Full-Stack Developer — all in one.

---

## The AppBana Agentic Mind

At the heart of AppBana lies a sophisticated AI Agent designed for precision and speed. It does not just "generate code"; it **reasons** through application requirements using a continuous execution cycle.

### The Think → Act → Observe Loop
1. **THINK** — The agent analyzes requirements, researches best patterns from the Knowledge Base, and formulates a multi-step execution plan.
2. **ACT** — The agent orchestrates a series of specialized tools to build components, wire up APIs, and architect databases.
3. **OBSERVE** — Every action is validated. The agent inspects tool outputs, identifies defects, and self-corrects in real time.

---

## Core Agentic Powers

### High-Concurrency Architecture
- **Java 21 Virtual Threads** — Independent tool calls execute in parallel using lightweight virtual threads, enabling rapid application assembly without blocking resources.
- **Batched Scaffolding** — A "one-shot" scaffolding engine creates entire application structures (entities, pages, relationships) in a single optimized session.

### Intelligent Optimizers
- **Semantic Caching** — Reduces cost and latency by caching high-level architectural decisions. Similar requests trigger instant reasoning from past execution context.
- **Pattern Matching Engine** — A zero-cost optimization layer that detects common development patterns and executes them instantly, bypassing expensive LLM calls for routine tasks.

### RAG-Driven Knowledge Base
- **Contextual Intelligence** — The agent is natively integrated with 39+ AppBana core schemas indexed in a Qdrant vector database.
- **Pattern Retrieval** — Retrieval-Augmented Generation (RAG) ensures every app follows established software-engineering best practices.

### Self-Healing & Zero-Defects
- **Metadata Validation** — Automated post-processing of AI outputs to guarantee 100% compliance with system constraints.
- **Auto-Correction** — When a schema migration or UI component fails, the agent observes the error and re-generates a corrected version automatically.

---

## Quick Start (Windows)

The only supported way to start every service in the correct order:

```powershell
.\start-everything.bat
```

This script:
1. Kills any stale Java / Node processes.
2. Starts the **AI Builder** (port `8081`) and waits for it to be ready.
3. Starts the **core API** (port `8080`).
4. Starts the **Studio UI** (port `5173`) via Vite.

| Service | URL |
|---------|-----|
| Studio UI | http://localhost:5173 |
| Core API | http://localhost:8080/health |
| AI Builder | http://localhost:8081/health |
| Qdrant Dashboard | http://localhost:6333/dashboard |

For local development setup, PostgreSQL configuration, and troubleshooting, see [docs/guides/02-DEVELOPMENT_GUIDE.md](docs/guides/02-DEVELOPMENT_GUIDE.md).

---

## Documentation

**All documentation lives under [`docs/`](docs/README.md).** Start with the [Documentation Hub](docs/README.md) — it indexes every file by topic.

Direct links to the most-used documents:

- [System Architecture](docs/architecture/01-ARCHITECTURE.md)
- [Development Guide](docs/guides/02-DEVELOPMENT_GUIDE.md)
- [User Manual](docs/guides/04-USER_MANUAL.md)
- [AI Agent Design](docs/features/ai-agent.md)
- [Multi-Tenant Design](docs/features/multi-tenant-design.md)
- [Security Features](docs/features/SECURITY_FEATURES.md)
- [Product Roadmap](docs/planning/03-ROADMAP.md)
- [Active Tasks](docs/ACTIVE_TASKS.md)

---

## Repository Layout

```
app-bana/
├── ai-builder/          AI microservice (port 8081)
├── app-bana-service/    Core backend API (port 8080)
├── app-bana-ui/         Studio frontend (Vite + LitElement, port 5173)
├── builder-database/    RAG knowledge base seed data
├── docs/                All documentation
├── config.json          Runtime configuration (DB, LLM keys)
└── start-everything.bat One-shot launcher
```

For a deeper walkthrough of each subproject, see [docs/architecture/01-ARCHITECTURE.md](docs/architecture/01-ARCHITECTURE.md).
