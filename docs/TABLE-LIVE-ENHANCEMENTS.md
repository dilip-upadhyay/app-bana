## StudioTableLive Enhancements (Nov 15 2025)

### Executive summary
- **Table data interaction** now supports pagination, sticky headers, server-side filters, and multiple preset themes plus runtime overrides for both builders and end users.
- **Selection & bulk actions** include multi-select, bulk delete/export with confirmation modal, toast feedback, and developer toggles for bulk- and multi-select behaviors.
- **Row lifecycle & editing** received a view modal (dynamic or custom forms), inline row editing with type-aware inputs, and direct inline cell editing with Enter/✔ to save and Escape/✖ to cancel.
- **Runtime polish** adds custom theme tokens, snackbar notifications, and inline cell editing that persists via the backend `PUT /api/{entity}/{id}` endpoint.
- **AI builder impact**: update `builder-database/99-capabilities-index.json` and the AI instructions with this feature set so agents can surface the table subsystem when generating apps.

### Instructions impact
1. Reference the updated table capabilities when describing layout-aware components. Mention that `StudioTableLive` now manages read-only, row-view modal, inline row edit, and inline cell edit modes.
2. When guiding AI agents, emphasize that bulk delete requires the `confirmDelete` toggle and that the component emits `bulk-action`, `cell-edit-*`, and `row-view` events for integrations.
3. Advise end users to use the view modal for quick field inspection and inline editor for fast tweaks; inline edits commit through the `PUT /api/{entity}/{id}` endpoint handled by `updateRow` in `api-client.ts`.

### Builder database & docs refresh
- Bump the builder database index (`99-capabilities-index.json`) to `1.0.1`, update the `lastUpdated` timestamp, and summarize the new table-focused capabilities under `recentEnhancements`.
- Update `docs/README.md` to link to this page so future contributors can find the latest runtime updates.
- Add a `studio-table-live` entry to `builder-database/02-components.json` covering its props, events, and runtime behaviors so AI agents can model the metadata-driven table experience.