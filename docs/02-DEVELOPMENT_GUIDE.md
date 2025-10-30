# 2. DEVELOPMENT SETUP & GUIDE

**Last Updated:** October 30, 2025  
**Status:** Active - Primary Reference for Development  
**Audience:** Developers, DevOps, QA

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Prerequisites](#prerequisites)
3. [Building the Project](#building-the-project)
4. [Running Locally](#running-locally)
5. [Configuration Guide](#configuration-guide)
6. [Frontend Development](#frontend-development)
7. [Backend Development](#backend-development)
8. [Testing](#testing)
9. [Troubleshooting](#troubleshooting)
10. [Studio Builder Guide](#studio-builder-guide)
11. [Keyboard Shortcuts](#keyboard-shortcuts)

---

## Quick Start

### For Backend-Only Development

```bash
# Clone and build
git clone <repo-url>
cd app-bana
./app-bana-service/mvnw clean package -DskipTests

# Run server
java -jar app-bana-service/target/app-bana-1.0-SNAPSHOT-fat.jar

# Visit
open http://localhost:8080
```

**What's available:**
- Schema management UI: `/`
- Swagger UI: `/ui/swagger`
- Datasource management: `/ui/datasource`

### For Full-Stack Development

```bash
# Terminal 1: Backend (watch mode)
cd app-bana-service
./mvnw clean package -Dappbana.port=8080

# Terminal 2: Frontend (dev server with HMR)
cd app-bana-ui
npm install
npm run dev

# Visit
open http://localhost:5173
```

**What's available:**
- Frontend dev server: `http://localhost:5173` (with hot reload)
- Backend API: `http://localhost:8080` (proxied)
- Studio Builder: `http://localhost:5173/studio`

---

## Prerequisites

### System Requirements

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| Java | 21 LTS | 25 LTS |
| Node.js | 18 | 20+ |
| npm | 9 | 10+ |
| Git | 2.30+ | Latest |
| RAM | 4 GB | 8+ GB |
| Disk | 2 GB | 5+ GB |

### Installation

#### Java 25 (macOS with Homebrew)

```bash
# Install Java 25 LTS
brew install openjdk@25

# Verify
java -version  # Should show "Java 25.x.x"

# Set JAVA_HOME if needed
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
```

#### Node.js

```bash
# Install Node.js 20+ (nvm recommended)
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.0/install.sh | bash
nvm install 20
nvm use 20

# Verify
node --version  # Should show v20.x.x
npm --version   # Should show 10+
```

---

## Building the Project

### Build Entire Project (All Modules)

```bash
# From root directory
cd app-bana

# Build all (frontend + backend + package)
./app-bana-service/mvnw clean package -DskipTests

# Includes:
# - Compiles TypeScript frontend
# - Builds Vite bundle → app-bana-ui/target/
# - Copies UI assets into service JAR
# - Creates executable service JAR: app-bana-service/target/app-bana-1.0-SNAPSHOT-fat.jar
```

### Build Backend Only

```bash
cd app-bana-service
./mvnw clean package -DskipTests

# Output: target/app-bana-1.0-SNAPSHOT-fat.jar
```

### Build Frontend Only

```bash
cd app-bana-ui

# Install dependencies
npm install

# Compile TypeScript + build Vite bundle
npm run build

# Output: src/main/resources/ui/dist/
```

### Verify Build

```bash
# After `npm run build` in app-bana-ui
npm run verify

# Checks:
# 1. Build succeeded
# 2. dist/index.html exists
# 3. Studio entry point is bundled
```

---

## Running Locally

### Option 1: Run Compiled JAR

```bash
# After building
java -jar app-bana-service/target/app-bana-1.0-SNAPSHOT-fat.jar

# With custom port
java -jar ... -Dappbana.port=9090

# With env var
APPBANA_PORT=9090 java -jar ...

# With auth tokens
APPBANA_ADMIN_TOKEN=admin123 APPBANA_READ_TOKEN=read123 java -jar ...
```

**Ports & Endpoints:**
- Backend API: `http://localhost:8080`
- Health: `http://localhost:8080/health`
- Swagger: `http://localhost:8080/ui/swagger`
- Datasources: `http://localhost:8080/ui/datasource`

### Option 2: Dev Server (Frontend + Backend)

**Terminal 1 - Backend:**
```bash
cd app-bana-service
./mvnw clean package -DskipTests
java -jar target/app-bana-1.0-SNAPSHOT-fat.jar
```

**Terminal 2 - Frontend:**
```bash
cd app-bana-ui
npm install
npm run dev

# Dev server runs on http://localhost:5173
# Proxies API calls to http://localhost:8080
```

### Option 3: Docker (Future)

```bash
# When Dockerfile is added
docker build -t app-bana .
docker run -p 8080:8080 app-bana
```

---

## Configuration Guide

### Configuration Hierarchy (Priority Order)

1. **Environment Variables** (highest)
   ```bash
   APPBANA_PORT=9090
   APPBANA_CONFIG=/path/to/config.json
   APPBANA_ADMIN_TOKEN=admin123
   APPBANA_READ_TOKEN=read123
   APPBANA_HTTPS_ENABLED=true
   APPBANA_KEYSTORE_PATH=/path/to/keystore.p12
   APPBANA_KEYSTORE_PASSWORD=password
   ```

2. **JVM System Properties**
   ```bash
   java -jar ... \
     -Dappbana.port=9090 \
     -Dappbana.config=/path/to/config.json
   ```

3. **Config File** (JSON)
   ```json
   {
     "port": 8080,
     "httpsEnabled": false,
     "serverType": "jdk",
     "datasources": [
       {
         "name": "default",
         "type": "h2",
         "url": "jdbc:h2:file:./appbana-db",
         "username": "sa",
         "password": "",
         "poolSize": 10,
         "connectionTimeout": 30000,
         "idleTimeout": 600000
       }
     ]
   }
   ```

4. **Defaults** (lowest)
   - Port: 8080
   - Database: H2 in-memory
   - Server: JDK HttpServer

### HTTPS Configuration

```bash
# 1. Generate PKCS12 keystore
keytool -genkeypair \
  -alias appbana \
  -keyalg RSA \
  -keysize 2048 \
  -keystore keystore.p12 \
  -storetype PKCS12 \
  -storepass mypassword \
  -validity 365

# 2. Start with HTTPS
APPBANA_HTTPS_ENABLED=true \
APPBANA_KEYSTORE_PATH=./keystore.p12 \
APPBANA_KEYSTORE_PASSWORD=mypassword \
java -jar target/app-bana-1.0-SNAPSHOT-fat.jar

# 3. Visit
open https://localhost:8080
# (Ignore self-signed cert warning in dev)
```

### Multi-Datasource Setup

```bash
# 1. Start server
java -jar target/app-bana-1.0-SNAPSHOT-fat.jar

# 2. Add datasources via UI
# Visit http://localhost:8080/ui/datasource
# Click "Add Datasource"
# Fill: Name, Type, URL, Username, Password
# Click "Test Connection"
# Save

# 3. Persist datasources
# Auto-saved to: ./appbana-config/datasources.json

# 4. Restart server
# Datasources restored on startup
```

---

## Frontend Development

### Project Structure

```
app-bana-ui/
├── src/
│   ├── index.ts                    # Main entry point
│   ├── app-renderer.ts             # Page renderer
│   ├── schema-builder.ts           # Schema editor
│   ├── studio-entry.ts             # Studio builder entry
│   ├── builder/
│   │   ├── components/
│   │   │   ├── AppManager.*        # App lifecycle
│   │   │   ├── BuilderCanvas.*     # Visual tree editor
│   │   │   ├── BuilderInspector.*  # Property editor
│   │   │   └── TokenPanel.*        # Design tokens
│   │   └── store/
│   │       ├── AppStore.ts         # App state
│   │       └── TreeStore.ts        # Component tree state
│   ├── components/
│   │   ├── app-sidebar.*           # Navigation
│   │   ├── component-gallery.*     # Component showcase
│   │   ├── entity-explorer.*       # CRUD interface
│   │   ├── ButtonElement.*         # Button component
│   │   ├── TextElement.*           # Text component
│   │   ├── ContainerElement.*      # Layout container
│   │   └── UnknownElement.*        # Fallback
│   ├── core/
│   │   ├── api-client.ts           # HTTP client
│   │   ├── api-service.ts          # Service layer
│   │   ├── api-extensions.ts       # FHIR, Workflow, etc.
│   │   ├── api-healthcare.ts       # HIPAA interceptors
│   │   ├── api-logistics.ts        # PWA, offline
│   │   ├── BaseElement.ts          # Component base class
│   │   ├── registry.ts             # Component registry
│   │   └── index.ts                # Core exports
│   ├── models/
│   │   ├── app-metadata.ts         # App/page models
│   │   ├── metadata.ts             # Interfaces
│   │   └── schema.ts               # Schema models
│   ├── runtime/
│   │   └── renderer/
│   │       └── Renderer.ts         # Page renderer
│   ├── styles/
│   │   └── theme.css               # Global styles
│   └── vite-env.d.ts               # Type declarations
├── index.html                      # Main HTML
├── studio.html                     # Studio builder HTML
├── vite.config.ts                  # Vite config
├── vitest.config.ts                # Vitest config
├── tsconfig.json                   # TypeScript config
├── package.json
└── pom.xml

target/
├── classes/ui/                     # Built UI assets
└── test-classes/
```

### NPM Scripts

| Script | Purpose |
|--------|---------|
| `npm run dev` | Start Vite dev server (hot reload) |
| `npm run build` | Compile TypeScript + bundle for production |
| `npm run preview` | Preview production build locally |
| `npm run test` | Run Vitest tests |
| `npm run verify` | Verify build output |

### Development Workflow

```bash
cd app-bana-ui

# 1. Install dependencies (first time)
npm install

# 2. Start dev server
npm run dev
# Runs on http://localhost:5173
# Proxies /api, /schema, etc. to http://localhost:8080

# 3. Edit files
# src/builder/components/BuilderCanvas.ts
# Changes auto-reload in browser (HMR)

# 4. Commit changes
git add .
git commit -m "feat: add new builder feature"

# 5. Build for production
npm run build
```

### Component Development

#### Creating a New Component

1. **Create component files:**
   ```bash
   # From app-bana-ui/src
   mkdir components/MyComponent
   touch components/MyComponent/MyComponent.ts
   touch components/MyComponent/MyComponent.css
   touch components/MyComponent/MyComponent.html
   ```

2. **Implement TypeScript:**
   ```typescript
   // MyComponent.ts
   import { LitElement, html, css, unsafeCSS } from 'lit';
   import { customElement, state, property } from 'lit/decorators.js';
   import styles from './MyComponent.css?inline';

   @customElement('my-component')
   export class MyComponent extends LitElement {
     static styles = css`${unsafeCSS(styles)}`;

     @property({ type: String }) label = '';
     @state() private isActive = false;

     render() {
       return html`
         <div class="container">
           <button @click=${() => this.toggle()}>
             ${this.label}
           </button>
         </div>
       `;
     }

     private toggle() {
       this.isActive = !this.isActive;
     }
   }

   declare global {
     interface HTMLElementTagNameMap {
       'my-component': MyComponent;
     }
   }
   ```

3. **Add styles:**
   ```css
   /* MyComponent.css */
   :host {
     display: block;
   }

   .container {
     padding: 16px;
   }

   button {
     padding: 8px 16px;
     background: #2563eb;
     color: white;
     border: none;
     border-radius: 4px;
     cursor: pointer;
   }
   ```

4. **Add documentation:**
   ```html
   <!-- MyComponent.html -->
   <!-- 
     MyComponent - Custom component
     
     Properties:
       - label: string - Button text
     
     Events:
       - toggle: Fired when button clicked
     
     Usage:
       <my-component label="Click Me"></my-component>
   -->
   ```

5. **Register component:**
   ```typescript
   // In core/registry.ts or component init
   import { MyComponent } from '../components/MyComponent/MyComponent';
   registry.register('my-component', MyComponent);
   ```

#### Testing Components

```typescript
// MyComponent.test.ts
import { describe, it, expect, beforeEach } from 'vitest';
import { MyComponent } from './MyComponent';

describe('MyComponent', () => {
  let element: MyComponent;

  beforeEach(() => {
    element = document.createElement('my-component') as MyComponent;
    element.label = 'Test';
    document.body.appendChild(element);
  });

  it('renders label', () => {
    const button = element.shadowRoot?.querySelector('button');
    expect(button?.textContent).toContain('Test');
  });

  it('toggles on click', () => {
    const button = element.shadowRoot?.querySelector('button');
    button?.click();
    // Verify state changed
  });
});
```

---

## Backend Development

### Project Structure

```
app-bana-service/
├── src/
│   └── main/java/com/appbana/
│       ├── Main.java               # Entry point
│       ├── ApiServer.java          # HTTP server + routes
│       ├── SchemaManager.java      # Schema lifecycle
│       ├── JdbcManager.java        # DB abstraction
│       ├── ConfigManager.java      # Configuration
│       ├── AppConfig.java          # Config model
│       ├── DatasourceConfig.java   # Datasource model
│       ├── AuditLogService.java    # Audit logging
│       ├── OpenApiGenerator.java   # OpenAPI spec
│       ├── DriverUtil.java         # Database drivers
│       ├── TomcatServer.java       # Tomcat (optional)
│       ├── api/
│       │   └── Router.java         # HTTP routing framework
│       └── model/
│           └── EntitySchema.java   # Schema domain model
│   └── resources/
│       ├── application.properties
│       └── ui/                     # Built UI assets
└── target/
    └── app-bana-1.0-SNAPSHOT-fat.jar
```

### Development Workflow

```bash
cd app-bana-service

# 1. Build with hot reload
./mvnw clean package -DskipTests -X

# 2. Edit Java files
# src/main/java/com/appbana/ApiServer.java

# 3. Rebuild
./mvnw package -DskipTests

# 4. Restart Java process
# Kill existing: kill <pid>
# Start new: java -jar target/app-bana-1.0-SNAPSHOT-fat.jar
```

### Adding New Endpoints

```java
// In ApiServer.buildRouter()

// Add route
router.post("/api/custom", (req, res) -> {
  try {
    // Parse request
    String body = new String(req.body(), StandardCharsets.UTF_8);
    Map<String, Object> data = objectMapper.readValue(body, Map.class);

    // Process
    String result = processCustomLogic(data);

    // Response
    res.json(200, Map.of("result", result));
  } catch (Exception e) {
    LOG.error("Custom endpoint error", e);
    res.json(500, ApiServer.errorDetails(e));
  }
});
```

### Database Operations

```java
// Via JdbcManager
import java.sql.*;

// Get connection
Connection conn = JdbcManager.getConnection();

// Prepare statement
PreparedStatement pstmt = conn.prepareStatement(
  "SELECT * FROM person WHERE id = ?"
);
pstmt.setObject(1, id);

// Execute
ResultSet rs = pstmt.executeQuery();

// Convert
List<Map<String, Object>> rows = ApiServer.toList(rs);

// Response
res.json(200, rows);
```

---

## Testing

### Frontend Tests

```bash
cd app-bana-ui

# Run all tests
npm run test

# Run specific test file
npm run test -- BuilderCanvas.test.ts

# Watch mode
npm run test -- --watch

# Coverage
npm run test -- --coverage
```

### Backend Tests

```bash
cd app-bana-service

# Run all tests
./mvnw test

# Run specific test
./mvnw test -Dtest=ApiServerTest

# Skip tests during build
./mvnw package -DskipTests
```

### Manual Testing (Smoke Test)

```bash
# 1. Start backend
java -jar app-bana-service/target/app-bana-1.0-SNAPSHOT-fat.jar

# 2. Test health
curl http://localhost:8080/health
# Expected: { "status": "UP" }

# 3. Create schema
curl -X POST http://localhost:8080/schema \
  -H 'Content-Type: application/json' \
  -d '{"name":"person","fields":[{"name":"id","type":"long","primaryKey":true}]}'

# 4. List schemas
curl http://localhost:8080/schema
# Expected: ["person"]

# 5. Query entities
curl http://localhost:8080/api/person
# Expected: []

# 6. Insert entity
curl -X POST http://localhost:8080/api/person \
  -H 'Content-Type: application/json' \
  -d '{"id":1}'

# 7. Query again
curl http://localhost:8080/api/person
# Expected: [{"id":1}]
```

---

## Troubleshooting

### Java Build Issues

**Error: `Java 25 not found`**
```bash
# Check JAVA_HOME
echo $JAVA_HOME

# Set correct JDK
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
./mvnw clean package -DskipTests
```

**Error: `Maven wrapper not found`**
```bash
# Run from service directory
cd app-bana-service
./mvnw clean package -DskipTests
```

### Frontend Build Issues

**Error: `npm ERR! Cannot find module`**
```bash
cd app-bana-ui
rm -rf node_modules package-lock.json
npm install
npm run build
```

**Error: `vite-env.d.ts types not recognized`**
```typescript
// Verify vite-env.d.ts exists:
cat src/vite-env.d.ts

// Should contain:
declare module '*.css?inline' {
  const content: string;
  export default content;
}
```

### Development Server Issues

**Error: `Port 8080 already in use`**
```bash
# Find process using port
lsof -i :8080

# Kill process
kill -9 <pid>

# Or use different port
APPBANA_PORT=9090 java -jar ...
```

**Error: `CORS errors in browser`**
```bash
# Frontend running on 5173, backend on 8080?
# Verify vite.config.ts has proxy:

server: {
  proxy: {
    '/api': 'http://localhost:8080',
    '/schema': 'http://localhost:8080',
  }
}
```

**Error: `Component not rendering`**
```bash
# Check console for errors
# Verify component registered in registry
// In core/registry.ts
registry.register('my-component', MyComponentClass);

// Check custom element name matches
@customElement('my-component')
```

---

## Studio Builder Guide

### Quick Overview

The **Studio Builder** is AppBana's visual editor for designing pages without code.

**Access:** `http://localhost:5173/studio` (dev) or `/studio` (prod)

### 3-Level User Guide

#### 🟢 Beginner (5 Minutes)

**Goal:** Add your first component

1. **Select a Template** (Optional)
   - Click "📋 Templates" at top
   - Choose "Login Page" or "Blank"
   - Auto-populates canvas

2. **Drag Component**
   - Drag "Button" from left library to center canvas
   - See green drop indicators

3. **Edit Properties**
   - Click button (purple border)
   - Right panel: change "text" to "Click Me!"
   - See instant preview

**🎉 Success!** You've built your first component!

#### 🟡 Intermediate (15 Minutes)

**Goal:** Build a contact form

1. **Create Structure**
   - Drag Container to canvas
   - Drag FlexColumn inside container

2. **Add Fields**
   - Drag TextInput into FlexColumn (3 times)
   - Drag Button below inputs

3. **Customize**
   - Click each input → set placeholder: "Name", "Email", "Message"
   - Click button → set text: "Send"

**💡 Pro Tip:** Save as reusable template!

#### 🟠 Advanced (30 Minutes)

**Goal:** Multi-column dashboard layout

**Layout Patterns:**

```
Grid (2 columns)
├── Card
│   ├── Heading
│   └── Content
└── Card
    ├── Heading
    └── Content
```

- Drag Grid to canvas
- Drag Card into left cell
- Drag Card into right cell
- Populate with content

### Common Actions

| Action | Steps |
|--------|-------|
| **Add Component** | Drag from library to canvas |
| **Select** | Click component on canvas |
| **Delete** | Select + press Backspace |
| **Duplicate** | Select + Cmd/Ctrl+D |
| **Move** | Drag component on canvas |
| **Reorder** | Drag in left tree panel |
| **Edit Property** | Select + modify right panel |
| **Undo** | Cmd/Ctrl+Z |
| **Redo** | Cmd/Ctrl+Shift+Z |
| **Search** | Cmd/Ctrl+P |

---

## Keyboard Shortcuts

### Essential Shortcuts

| Action | macOS | Windows/Linux |
|--------|-------|---------------|
| Add Component | Drag from library | Drag from library |
| Select Component | Click on canvas | Click on canvas |
| Delete | Delete / Backspace | Delete / Backspace |
| Deselect | Escape | Escape |
| Duplicate | Cmd+D | Ctrl+D |
| Undo | Cmd+Z | Ctrl+Z |
| Redo | Cmd+Shift+Z | Ctrl+Y |
| Search Palette | Cmd+P | Ctrl+P |
| Copy ID | Shift+Cmd+C | Shift+Ctrl+C |
| Inline Edit | Enter | Enter |
| Expand/Collapse | Click arrow | Click arrow |

### Advanced Shortcuts

| Feature | Shortcut | Purpose |
|---------|----------|---------|
| Quick Search | Cmd/Ctrl+P | Find components by name/type |
| Copy Component ID | Shift+Cmd/Ctrl+C | For scripting/debugging |
| Inline Text Edit | Enter | Edit text component directly |
| Multi-select | Cmd/Ctrl+Click | Select multiple (planned) |
| Paste | Cmd/Ctrl+V | Paste from clipboard (planned) |

### Design Token Shortcuts

| Action | Shortcut |
|--------|----------|
| Undo Token Edit | Cmd/Ctrl+Z |
| Redo Token Edit | Cmd/Ctrl+Shift+Z |
| Search Token | Cmd/Ctrl+F (in token panel) |
| Export Tokens | Cmd/Ctrl+E (planned) |
| Import Tokens | Cmd/Ctrl+I (planned) |

---

## Useful Commands

### Quick Development Commands

```bash
# Backend build + run
cd app-bana-service && ./mvnw package -DskipTests && java -jar target/app-bana-1.0-SNAPSHOT-fat.jar

# Frontend build + preview
cd app-bana-ui && npm install && npm run build && npm run preview

# Full stack dev
# Terminal 1:
cd app-bana-service && ./mvnw package -DskipTests && java -jar target/app-bana-1.0-SNAPSHOT-fat.jar

# Terminal 2:
cd app-bana-ui && npm install && npm run dev

# Run tests
cd app-bana-ui && npm test
cd app-bana-service && ./mvnw test

# Format code
cd app-bana-ui && npm run format  # If formatter configured
```

---

## References

- **Architecture:** See `01-ARCHITECTURE.md`
- **Roadmap:** See `03-ROADMAP.md`
- **API Client:** See `app-bana-ui/src/core/API_CLIENT_README.md`
- **Code Examples:** See `app-bana-ui/src/core/api-examples.ts`

