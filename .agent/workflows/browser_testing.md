---
description: Mandatory protocol for testing the AppBana application in the browser
---

# Browser Testing Protocol

**IMPORTANT**: Before starting ANY browser test or when asked to test the application, you MUST follow this protocol to understand the application context.

## 1. Context Acquisition (Required)

Read the following documentation in the `automation-test/` folder to establish context:

1. **Overview & Index**:
   - `view_file automation-test/README.md`
   - Understand the folder structure and available resources.

2. **Test Plan & Scenarios**:
   - `view_file automation-test/qa_test_plan.md`
   - Identify which scenario matches the current request.
   - Note the **login credentials** (`test@example.com` / `Password123`).
   - Note the **environment URLs** (Studio: `http://localhost:5173/studio`).

3. **Visual Reference**:
   - `view_file automation-test/visual_test_guide.md`
   - Review screenshots to understand the UI layout and expected element locations.
   - Pay special attention to the correct Entity Creation flow.

## 2. Environment Setup

*   **Studio URL**: `http://localhost:5173/studio`
*   **Pipeline Access**: Use "Pipeline" button in top header
*   **Runtime Testing**: ALWAYS usage **Development (DEV)** environment via Pipeline "Open App" button.

## 3. Execution Standard

*   Always refer to the specific steps in `qa_test_plan.md`.
*   **Handle Autofill**: Always CLEAR input fields (select all + delete) before typing to prevent double-entry (e.g. `user@example.comuser@example.com`).
*   Verify critical checkpoints (e.g., proper field names, visual indicators).
*   Report bugs using the format defined in the test plan.

## 4. Continuous Improvement (Mandatory)

**After every test session, you MUST:**

1.  **Capture New Screenshots**:
    *   If the UI has changed or if testing coverage expands to new areas, take new screenshots.
    *   Save them to the `automation-test/` or parent artifacts folder.

2.  **Update Visual Documentation**:
    *   Update `automation-test/visual_test_guide.md` with the new screenshots.
    *   Ensure the guide reflects the *current* state of the application.

3.  **Refine Test Plan**:
    *   If you discover new edge cases or if existing steps are outdated, update `automation-test/qa_test_plan.md` immediately.
    *   Do not leave documentation stale.

4.  **Log Findings**:
    *   If significant new patterns are found, add them to `automation-test/README.md` or a new "lessons learned" section.

