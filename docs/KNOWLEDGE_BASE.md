# AppBana Knowledge Base

## 1. Multi-Tenant Schema Architecture
The AppBana backend uses a strict **Multi-Tenant Database Isolation Protocol**. 

### The Rule
When interacting with endpoints like `POST /api/{entity}/batch` or executing backend SQL functions, you CANNOT simply hit `/api/Customer`. 
AppBana's `SchemaManager.java` physically stores the schemas and resolves URLs using this exact key prefix format:
`{tenantId}_{appId}_{entityName}`

### Example Execution
If an agent scaffolded an application with `appId = 7495460a-bc30-40e9-8235-9ddb08720b2a` and `tenantId = default` with an entity named `Customer`, the resulting internal UUID and REST URL for fetching/mocking data physically becomes:
**`default_7495460a-bc30-40e9-8235-9ddb08720b2a_Customer`**

## 2. Boot Service Protocol (`start-everything.bat`)
When running AppBana locally, you **MUST** use `start-everything.bat`.

### Architecture
- **Step 0**: Aggressively terminates any existing `java` and `node` background processes to resolve `8080/8081` port race conditions.
- **Step 1**: Launches `start-ai-builder.bat` in a separate `cmd` window (which compiles Maven and spins up port `8081`).
- **Step 2**: Launches Vite UI.
- **Step 3**: Dynamically polls for `127.0.0.1:8081`. ONLY when the port responds will it spin up the main Backend Service (`app-bana-service`) on port `8080`.

## 3. Entity Specification Loop (AiAgent)
The AI Agent must NEVER execute `create_entity` or `scaffold_app` prior to obtaining user confirmation.
1. Output a plain-English user specification (`GATHERING_REQUIREMENTS` state).
2. Ask "Shall we build this?"
3. On user agreement, execute `scaffold_app`.
