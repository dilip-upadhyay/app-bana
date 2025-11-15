# AppBana Builder Database

**Purpose**: Complete reference database for AI agents to understand all app building possibilities in AppBana.

**Last Updated**: November 15, 2025  
**Version**: 1.0.1

## Overview

This directory contains machine-readable metadata that describes ALL capabilities of the AppBana platform. AI agents use these files to:
- Understand what can be built
- Generate valid app metadata
- Create components, pages, entities, and relationships
- Configure datasources and styling
- Build complete applications through chat interface

## Database Structure

```
builder-database/
├── README.md                      # This file
├── 01-core-concepts.json          # Fundamental concepts and architecture
├── 02-components.json             # All available UI components with props
├── 03-entities.json               # Entity field types and relationships
├── 04-pages.json                  # Page templates and structure
├── 05-datasources.json            # Datasource adapters and configs
├── 06-styling.json                # Design tokens and theme system
├── 07-validation.json             # Validation rules and patterns
├── 08-api-endpoints.json          # REST API reference
└── 99-capabilities-index.json     # Quick lookup index
```

## Update Protocol

**CRITICAL**: When codebase changes, update these files incrementally:

### When to Update

1. **New Component Added** → Update `02-components.json`
2. **Entity Field Type Added** → Update `03-entities.json`
3. **Page Template Added** → Update `04-pages.json`
4. **Datasource Adapter Added** → Update `05-datasources.json`
5. **Design Token Changed** → Update `06-styling.json`
6. **Validation Rule Added** → Update `07-validation.json`
7. **API Endpoint Changed** → Update `08-api-endpoints.json`

### Update Process

1. Identify which capability file(s) need updates
2. Read current file content
3. Add new entries preserving existing structure
4. Update version and lastUpdated timestamp
5. Update `99-capabilities-index.json` with summary

### Example Update
```typescript
// When adding new component to src/components/NewComponent.ts
// Update builder-database/02-components.json:
{
  "components": [
    // ... existing components
    {
      "type": "new-component",
      "name": "NewComponent",
      "description": "Brief description",
      "props": { /* all properties */ },
      "examples": [ /* usage examples */ ]
    }
  ],
  "version": "1.0.1", // increment version
  "lastUpdated": "2025-11-08T12:00:00Z" // update timestamp
}
```

## Usage by AI Agents

### Building an App
1. Read `99-capabilities-index.json` for quick overview
2. Read relevant capability files based on user requirements
3. Generate valid metadata conforming to schemas
4. Use AppStore API to create app/pages/entities
5. Reference examples from capability files

### Chat-Based Builder Flow
```
User: "Create a blog app with posts and comments"
AI:   1. Reads 03-entities.json → understands entity capabilities
      2. Reads 03-entities.json → finds relationship types
      3. Reads 04-pages.json → finds CRUD page templates
      4. Generates metadata:
         - Post entity (title, content, author fields)
         - Comment entity (content, author fields)
         - One-to-many relationship (Post → Comments)
         - List page, Detail page, Create page
      5. Calls AppStore.createApp() with generated metadata
```

## StudioTableLive Metadata

- **Component guidance**: Reference `builder-database/02-components.json` for the `studio-table-live` entry that enumerates props (entity, fields, pagination, multi-select, bulk actions, viewMode, inline cell editing) and emitted events for AI-integrated builders.
- **Documentation**: Link AI prompts to `docs/TABLE-LIVE-ENHANCEMENTS.md` and `builder-database/99-capabilities-index.json` so that pagination, theming, view/edit modal, toast, and inline edit capabilities are surfaced when generating metadata descriptions.

## Validation

All generated metadata must conform to TypeScript interfaces:
- `PageMeta` (src/models/metadata.ts)
- `EntityMeta` (src/models/entity-metadata.ts)
- `ComponentNode` (src/models/metadata.ts)
- `DataSourceConfig` (src/core/DataSourceAdapter.ts)

## Version History

- **1.0.1** (Nov 15, 2025) - Added `studio-table-live` component metadata and refreshed references for the table runtime.
- **1.0.0** (Nov 8, 2025) - Initial database creation with 8 capability files

## Breaking Changes / Important Notes (Nov 10, 2025)

- API: `GET /apps` now returns a wrapped response: `{ "apps": [ ... ] }` (not a raw array). Update generated clients to read `response.apps`.
- Frontend: `AppStore.setCurrentApp()` is asynchronous and loads the full app (entities/pages). Callers should `await appStore.setCurrentApp(appId)` before reading entities/pages.

If you generate client code or agent flows from these capability files, ensure examples use the wrapper and await `setCurrentApp()` where applicable.
