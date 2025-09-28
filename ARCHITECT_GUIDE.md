# AppBana — Architect and Developer Guide

This document provides a comprehensive overview of the AppBana application, intended for architects, developers, and product owners who need to understand the system's design, capabilities, and technical direction.

## 1. Product Vision & Architecture

**Vision:** AppBana aims to be the dominant platform for rapidly building secure, complex, and scalable enterprise solutions, with a focus on Healthcare, Logistics, and HR Management.

**Core Architecture:** AppBana is a **metadata-driven platform**. The core principle is to design data schemas in a UI, which the system then uses to automatically:
1.  Persist the schema.
2.  Create or migrate a backing database table.
3.  Expose a full set of runtime CRUD (Create, Read, Update, Delete) APIs for that schema.

This end-to-end cohesion, from database to UI, is the primary competitive advantage.

**Tech Stack:**
- **Backend:** Java 25 with virtual threads, using the built-in `HttpServer`. It's designed to be lightweight with no heavy frameworks.
- **Database:** H2 (embedded file-based) by default, but any JDBC-compliant database (Postgres, MySQL, etc.) can be used.
- **Connection Pooling:** HikariCP for efficient database connection management.
- **JSON Processing:** Jackson (`jackson-databind`).
- **Logging:** SLF4J.
- **Build:** Maven with the Shade plugin to create an executable "uber jar".
- **Frontend:** Current minimal UIs use vanilla JS, with an Angular 21 UI being developed.

## 2. Key Features and Capabilities

### 2.1 Dynamic Schema Management
- Visual schema builder allows defining entity models in a UI.
- Preview migration before applying to see the DDL changes.
- Schema persistence and versioning.
- Auto-generates tables and migrations.

### 2.2 Datasource Management
- Support for multiple datasources (H2, PostgreSQL, MySQL, MariaDB, SQL Server, Oracle, SQLite)
- UI for adding, testing, and managing database connections
- Switch the active datasource at runtime
- Connection pooling with HikariCP, with configurable settings per datasource
- URL Builder to assist with JDBC connection strings

### 2.3 API Generation
- Automatic CRUD API endpoints for each schema
- Live OpenAPI 3.0 specification
- Embedded Swagger UI for API exploration
- Health and readiness endpoints

### 2.4 Security
- Optional token-based authentication
- Configurable admin and read-only access
- HTTPS support with custom keystore
- SQL injection protection via prepared statements

### 2.5 Planned Features (Q4 2025)
- Stateful Workflow Engine for complex, multi-step processes
- Advanced security with comprehensive audit trails
- Field-Level Security (FLS) to restrict access to specific data fields
- Plugin architecture for custom components
- PWA for offline operation
- Real-time data via WebSockets
- Healthcare interoperability via FHIR
- Reporting and export capabilities

## 3. System Architecture

### 3.1 Current Architecture
The current codebase is a functional MVP with minimal abstractions:

- `ApiServer.java`: Handles HTTP requests and routing
- `SchemaManager.java`: Manages schema persistence and migrations
- `JdbcManager.java`: Manages database connections and pooling
- `ConfigManager.java`: Configuration loading and management
- `OpenApiGenerator.java`: Generates the OpenAPI specification

### 3.2 Planned Refactoring
A refactoring is underway to improve maintainability and testability:

- Clear package boundaries (api, schema, db, config, openapi, util)
- Database dialect abstraction for better multi-DB support
- Improved HTTP layer with standardized request/response handling
- Better type safety and validation
- Comprehensive test coverage

## 4. Angular UI Architecture (Q4 2025)

The upcoming Angular 21-based UI is structured around:

### 4.1 Workspace Structure
- Nx monorepo with:
  - apps/studio: The designer application
  - libs/runtime: The renderer library
  - libs/designer: Designer components
  - libs/ui-schema: Schema models and services

### 4.2 Key UI Concepts
- Component Registry: A DI-based registry for UI components
- Plugin Architecture: Support for custom components and data connectors
- Material-based styling with CSS variables for theming
- Token-based authentication with secure storage
- Runtime FLS enforcement for field visibility/disabling

### 4.3 Designer and Runtime
- Designer: Visual interface for building applications
- Runtime: Renderer that displays applications built with the designer
- Data binding and event handling between components
- Settings panel for configuration

## 5. Vertical-Specific Features

### 5.1 Healthcare
- HIPAA-compliant audit trails
- Field-level security for PHI
- FHIR connector for interoperability
- Patient History Timeline component

### 5.2 Logistics
- Real-time operations via WebSockets/MQTT
- PWA with offline support for field operations
- Barcode/QR scanner component
- Map components with geo-tracking
- Exception rules and alerts
- Multi-tenant data partitioning

### 5.3 HR Management
- Multi-step approval workflows
- Relationship-based permissions
- Report generation and export
- Document management

## 6. Development, Testing, and Deployment

### 6.1 Building and Running
- Java backend:
  ```bash
  ./mvnw -DskipTests package
  java -jar target/app-bana-1.0-SNAPSHOT-fat.jar
  ```
- Angular UI (future):
  ```bash
  ./build.sh --clean
  ./run.sh --port 4000 --open
  ```

### 6.2 Configuration Options
- Port: `-Dappbana.port=9090` or `APPBANA_PORT=9090`
- Config file: `APPBANA_CONFIG` or `-Dappbana.config=...`
- HTTPS: `APPBANA_HTTPS_ENABLED=true`, etc.
- Authentication: `APPBANA_ADMIN_TOKEN`, `APPBANA_READ_TOKEN`

### 6.3 Testing
- Smoke test available in `UI_SMOKE.md`
- Future: Unit tests and integration tests with Testcontainers

## 7. Security Considerations

- Token-based authentication should be enabled in production.
- Passwords are never exposed in API responses.
- HTTPS is recommended for production deployments.
- Audit logging will track all data access and changes.
- Field-Level Security will control granular data access.

## 8. Future Directions and Extensions

- Spring Boot adapter (optional)
- Docker and Helm packaging
- PDF report rendering
- Advanced FHIR capabilities (write operations, SMART on FHIR)
- Real-time collaboration in the designer
- DICOM viewer for medical imaging
- Enhanced pagination, sorting, and filtering for APIs
- Import/export functionality for schemas and datasources

## 9. Documentation and Resources

- `README.md`: Main project documentation
- `USER_GUIDE.md`: Step-by-step guide for users
- `UI_SMOKE.md`: Quick verification test
- `docs/REFACTOR_PROPOSAL.md`: Technical refactoring plan
- `docs/STYLE_GUIDE.md`: UI styling guidelines
- `TODO.md`: Prioritized backlog
- `OCT_2025_EPICS_STORIES.md`: Detailed user stories for October 2025
