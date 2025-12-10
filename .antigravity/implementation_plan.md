# Enhancement Plan: Conversation-to-Workflow

## Goal
Enable the AI Builder to generate executable business logic (Workflows) from natural language (e.g., "When a high-value loan is created, ask a manager for approval").

## 1. System Prompt Update
**File:** `com.appbana.ai.AiSystemPrompts.java`

- **Action:** Add a section "WORKFLOWS" to the `BASE_APP_GENERATION_PROMPT`.
- **Content:**
    - Explain concepts: Trigger (Create/Update), Nodes (Start, End, User Task, Service Task, Decision), Transitions.
    - specialized strict JSON schema for `workflows` array.
    - Example: "Payment Approval" workflow.

### Backend Logic (Process & Persist) - [x]
- [x] Modify `AiAppGeneratorService.java`:
  - [x] Update `GenerationResult` to include `List<WorkflowDefinition> workflows`.
  - [x] Update `parseAiResponse` to extract the `workflows` array.
  - [x] Implement `saveWorkflows(String appId, List<WorkflowDefinition> workflows)` helper method.
    - Use `JdbcManager` to `INSERT` or `UPDATE` into `appbana_wf_definition`.
    - Set status to 'ACTIVE' by default for AI-generated workflows for immediate testing.

## 2. Grid Layout Improvements (Auto-sizing Cells)

#### [MODIFY] [GridElement.ts](file:///c:/Users/dilip/git/app-bana/app-bana-ui/src/components/GridElement.ts)
*   Render `app-grid` logic will be updated to respect "auto" sizing.
*   Remove hardcoded `min-height: 100px` CSS rule, or change default to `auto`.
*   Remove `min-height: 60px` on `.cell-content`.
*   Ensure empty cells still have a minimum visual footprint in Builder mode (using `:empty` or helper class) but collapse in Runtime.

#### [MODIFY] [ComponentLibrary.ts](file:///c:/Users/dilip/git/app-bana/app-bana-ui/src/builder/components/ComponentLibrary.ts)
*   Update default cell styles to remove fixed `min-height` and border styles that conflict with `GridElement`'s own styles.
*   Let `GridElement` handle the visual "dashed box" via its internal CSS, rather than inline styles on the cell container.

## 3. Verification
- **Test:** Ask AI "Create a Leave Request app where a manager must approve leaves longer than 3 days".
- **Check:**
    1.  App is created.
    2.  `appbana_wf_definition` table contains the workflow.
    3.  `definition_json` has correct nodes/transitions.
