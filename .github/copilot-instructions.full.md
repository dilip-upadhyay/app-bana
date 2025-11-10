# GitHub Copilot Instructions for AppBana (Full Reference)

## Project Overview
AppBana is a **metadata-driven platform** that generates end-to-end functionality from a single source of truth. The core flow: `Entity Definition (Business Layer) → Schema (Technical Layer) → Database Table → REST CRUD APIs → UI Pages (Runtime)`. Changes to metadata propagate automatically through all layers.

## Technology Stack
- **Backend**: Java 21 LTS (corrected from 25), JDK HttpServer (default) or Tomcat, H2 embedded database, HikariCP, Jackson, Maven multi-module build
- **Frontend**: TypeScript 5.2.2+, Lit 3.1.4 Web Components, Vite 5.3.1+ dev server, Vitest 1.5.0+ testing
- **Architecture**: Shadow DOM component system, custom element registry, metadata-driven rendering, dual-layer abstraction (Entity → Schema), universal datasource adapters
- **Persistence**: Backend filesystem (apps/{appId}/app.json + pages/{pageId}.json) - migrated from localStorage Nov 2025

## Critical Development Patterns

### 1. Universal Datasource Adapter System (NEW - Nov 8, 2025)
**Entities can now work with ANY backend** - not just databases:
```
EntityMeta (Business Definition)
    ↓ Choose Datasource Type
REST API | SQL DB | NoSQL | Files | LocalStorage
    ↓ DataSourceAdapter Interface
Universal CRUD Operations
```

**Key Files**:
- `src/core/DataSourceAdapter.ts`: Universal CRUD interface (590 lines)
- `src/core/AdapterRegistry.ts`: Singleton adapter registry (255 lines)
- `src/core/adapters/RestApiAdapter.ts`: External REST APIs with auth, rate limiting (396 lines)
- `src/core/adapters/JsonFileAdapter.ts`: File/LocalStorage/SessionStorage (334 lines)
- `src/core/adapter-bootstrap.ts`: Auto-registers 7+ datasource types
- `src/core/ADAPTER_GUIDE.md`: Comprehensive usage guide (500+ lines)

... (rest of the original full content omitted in this file to keep size reasonable)