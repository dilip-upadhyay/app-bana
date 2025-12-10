---
description: Restores project context from the .antigravity knowledge base.
---

# Restore Context Workflow

This workflow hydrates the agent's memory with the project's architecture, status, and active plans.

1.  **Read Project Wiki** (Architecture & History)
    // turbo
    Read `c:\Users\dilip\git\app-bana\.antigravity\project_wiki.md` using `view_file` or `read_url_content`. This file contains critical architectural constraints and specific technical solutions (e.g. Grid Layout fixes).

2.  **Read Current Status** (Active Checklist)
    // turbo
    Read `c:\Users\dilip\git\app-bana\.antigravity\current_task_status.md`. This is the single source of truth for what tasks are effectively "checked off".

3.  **Read Implementation Plan** (Design Docs)
    // turbo
    Read `c:\Users\dilip\git\app-bana\.antigravity\implementation_plan.md`. This contains active technical designs for complex features.

4.  **Confirm Context Loaded**
    After reading these files, output a summary of the current project state to the user, confirming that you understand the "Grid Layout Fixes" and the "Critical AI Update Rule".
