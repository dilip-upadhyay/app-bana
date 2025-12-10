# AppBana Launch Playbook 🚀

> **Purpose**: This document is the "Flight Manual" for taking AppBana from development to production. Use this checklist before every release.

## 1. Pre-Flight Checks (The "Go/No-Go" List)

Before building, verify the following are functionally correct in `dev` mode:
- [ ] **Grid Layout**: Check the "Test" page. Are cells tight? Are "R1C1" labels hidden? (See `project_wiki.md` if issues persist).
- [ ] **Data Connectivity**: functionality `StudioForm` loads data? (Check Network tab for `/api/LoanApplication/...` 200 OK).
- [ ] **AI Service**: Can the text interface generate a new simple app? (Tests backend AI integration).
- [ ] **AI Sync**: Have all recent code changes (props, components) been reflected in `builder-database/` JSONs?

## 2. Build Procedure 🛠️

### Frontend (UI)
The UI must be built into static assets for production.
```bash
cd app-bana-ui
npm install      # Ensure deps are fresh
npm run build    # Output goes to /dist
```
*Artifact*: `app-bana-ui/dist/` (HTML/JS/CSS bundles)

### Backend (Service)
The Java backend must be packaged as a Fat JAR.
```bash
cd app-bana-service
mvn clean package -DskipTests
```
*Artifact*: `app-bana-service/target/app-bana-1.0-SNAPSHOT-fat.jar`

## 3. Deployment Strategy 🚢

### Application Server
The Fat JAR runs the HTTP server. It serves API endpoints AND static files (if configured).

**Production Run Command**:
```bash
java -jar -Dhttp.port=8080 -Dapp.env=prod app-bana-1.0-SNAPSHOT-fat.jar
```

### Static Assets
If using Nginx/Apache (Recommended for Market Launch):
1.  Point web root to `app-bana-ui/dist`.
2.  Proxy `/api/*` requests to `localhost:8080`.

If using Standalone Java (Easier):
Ensure `app-bana-service` is configured to serve `dist/` folder on `/`.

## 4. Verification Checklist Implementation

After deployment, perform "The Golden Flow" test:

1.  **Open Home**: Does the landing page load without console errors?
2.  **Create Entity**: Submit a new "Loan Application".
    - *Success Criteria*: "Saved successfully!" alert and database entry created.
3.  **View Grid**: Navigate to the "Back Office" / Grid page.
    - *Success Criteria*: Layout is clean, no dashed lines, no gaps. CSS variables should have resolved to transparent.
4.  **Edit Record**: Open a record ID (e.g., `?recordId=123`).
    - *Success Criteria*: Fields populate with data (Name, Amount).

## 5. Troubleshooting Guide 🔧

-   **Issue**: "Dotted Lines Visible in Production"
    -   *Cause*: `LivePreview.css` might have leaked into production build, OR `ContainerElement` default styles reverted.
    -   *Fix*: Check `project_wiki.md` ("The Grid Layout Gap Saga"). Ensure CSS variables are undefined in prod.

-   **Issue**: "Navigation Fails on Refresh" (404)
    -   *Cause*: SPA Routing (Lit Router) validation without server support.
    -   *Fix*: Configure server Deployment to redirect all 404s to `index.html`.

## 6. Emergency Contacts
-   **Lead Dev**: [Your Name/Team]
-   **AI Agent Context**: See `.antigravity/` folder for system history.
