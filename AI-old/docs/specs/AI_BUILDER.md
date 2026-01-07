# AI Builder Specification

## 1. Vision

The AI Builder is a conversational agent that lets non-technical users describe business problems in plain language and gradually generate and evolve full applications in AppBana (apps, entities, fields, pages, and relationships). It should feel like talking to a product designer who understands business language, not like programming.

Key goals:
- Map natural language to AppBana capabilities (apps, entities, fields, pages, datasources).
- Keep the backend metadata as the single source of truth.
- Use external AI (GPT) only when necessary; otherwise rely on internal rules, context, and learned patterns.
- Provide clear, human-friendly feedback with every action.

## 2. Core Capabilities

### 2.1 Conversational capabilities

The builder must support at least these categories of interactions:

1. **Small talk & onboarding**
   - Recognize greetings / chit-chat ("hi", "how are you", "thanks", "bye").
   - Respond politely and then guide users back to app-building prompts.
   - Must NOT trigger data operations on small talk.

2. **App management**
   - List apps: "show my apps", "list all applications".
   - Open apps by index or name: "open the second app", "load Restaurant Management App".
   - Delete apps: "delete this app", "remove the project management app".

3. **App creation**
   - High-level descriptions: "I need a project management system with projects, tasks, and team members".
   - Builder infers entities, relationships, and initial pages (list/detail/form/dashboard) using AI + templates.
   - Returns a summary of what was created in business language.

4. **Entity & field design**
   - Add/modify/remove fields: "add a status field to Task; values: todo, in progress, done", "make due date required", "remove the priority field".
   - Rename or adjust entities and fields: "rename customer to client everywhere".

5. **Page design & wiring**
   - Create pages: "create a dashboard with open tasks by status", "add a form for creating new orders".
   - Modify page content: "on the order list, show customer name and total amount".

6. **Exploration & introspection**
   - Ask what exists: "what entities does this app have?", "show me the fields on Task", "what pages are available for Orders?".


## 3. Architecture Overview

Pipeline for each incoming message:

1. **Conversation Manager**
   - Entry point (currently `AiAppGeneratorService.generateApp`).
   - Assembles `GenerationRequest` including `userId` and `conversationContext`.
   - Coordinates the rest of the pipeline and returns `GenerationResult`.

2. **Intelligence Engine (internal brain)**
   - Components:
     - Rule & pattern engine (regex + heuristics).
     - Intent cache (learned mappings from natural language to actions).
     - SmallTalkEngine integration.
   - First line of defense; tries to handle requests without calling external AI.

3. **AI Connector (external GPT)**
   - Only used when the Intelligence Engine cannot confidently classify or design.
   - Two main prompts:
     - Action classifier: return structured `{ action, target, options }`.
     - App designer: given a high-level app description, propose entities, relationships, pages.

4. **Action Executor**
   - Maps actions to AppBana operations:
     - `AppManager` for app-level operations.
     - Entity/page managers for schema and page operations.
   - Updates metadata on disk.
   - Returns structured `payload` and a human-friendly `reply` string.

5. **Memory & Learning Store**
   - Per-user conversation context (via `AgentMemoryService` or equivalent).
   - Global intent cache for repeated patterns.
   - Simple JSON or file-based store initial implementation.


## 4. Conversation Context

Per `userId`, we maintain a lightweight context structure such as:

```json
{
  "userId": "user:default",
  "currentAppId": "restaurant-app",
  "currentAppName": "Restaurant Management App",
  "lastAppList": [
    { "id": "project-management", "name": "Project Management App" },
    { "id": "restaurant-app", "name": "Restaurant Management App" }
  ],
  "currentEntity": null,
  "currentPageId": null,
  "lastAction": "listApps"
}
```

Use cases:
- Resolve pronouns and implicit references: "this app", "that page".
- Resolve indices: "open the second app", "delete the third page".
- Provide better prompts to GPT when needed (include context).


## 5. Action Model

We treat every message as leading to an `ActionDescriptor`:

```json
{
  "action": "loadApp",             // e.g. listApps | loadApp | deleteApp | generateApp | listPages | createEntity | ...
  "target": {
    "type": "app",                // app | entity | page | field | datasource | null
    "name": "Restaurant Management App",
    "index": 2                     // 1-based index into lastAppList, optional
  },
  "options": {
    "appId": "restaurant-app",    // additional parameters, resolved later if missing
    "appName": "Restaurant Management App"
  }
}
```

The backend is responsible for:
- Producing this descriptor (via rules, cache, or AI classifier).
- Executing it and resolving missing pieces from context (e.g., `index`, `name` → `appId`).


## 6. Intelligence Engine

### 6.1 Rule & pattern engine

- Small talk detection:
  - Patterns for greetings, thanks, generic chit-chat.
  - Route to `SmallTalkEngine` with no side effects on data.

- App-level commands:
  - `listApps`: "show my apps", "list all apps", "what apps do I have".
  - `loadApp` by index or name: "open the second app", "load Restaurant Management App".
  - `deleteApp`: "delete this app", "remove project management app".

Implementation:
- Add helper methods in `AiAppGeneratorService` (or a new `IntelligenceEngine` class) that:
  - Normalize text (lowercase, trim, remove punctuation).
  - Use regex/keywords to produce `ActionDescriptor` when confident.

### 6.2 Intent cache

- Purpose: avoid calling GPT for repetitive commands.
- Data:
  - `normalizedText` → `ActionDescriptor` + usage metadata.
- Storage:
  - Start with a JSON file, e.g. `app-bana-service/ai-mem/intent-cache.json`.
  - Load to memory on startup; flush changes periodically.
- Behavior:
  - On each message, try cache lookup before calling GPT.
  - When GPT returns a valid action, store it in cache for future reuse.


## 7. AI Connector (GPT)

Used when:
- No rule matches and no cache hit for the message.
- The message is a high-level app design request.

Two main prompt types:

1. **Action classifier**
   - Input: user text, conversation context summary, and a description of supported actions.
   - Output: JSON `ActionDescriptor`.
   - Validation: `AiResultValidator` ensures required fields exist and values are within allowed actions.

2. **App designer**
   - Input: business description of the desired app plus relevant AppBana capabilities (`builder-database`).
   - Output: entities, fields, relationships, pages, and initial layout hints.
   - Validation and adaptation into `AppMetadata` and `PageMeta` structures.


## 8. Action Executor

Responsibilities:
- Take an `ActionDescriptor` and `conversationContext`.
- Resolve target IDs:
  - For `loadApp`:
    - If `options.appId` present → use directly.
    - Else if `target.index` and `lastAppList` exist → resolve using that index.
    - Else if `target.name` present → resolve by matching against `lastAppList` or `AppManager.listApps()`.
- Call appropriate backend services (`AppManager`, entity/page managers).
- Update conversation context (e.g., `currentAppId` after load, `lastAppList` after list).
- Build `GenerationResult`:

```json
{
  "success": true,
  "payload": { ... },            // e.g., apps, app, pages
  "reply": "Opened Restaurant Management App with 3 pages."
}
```

Errors must be human-friendly (no internal option/validation messages), e.g.:
- "I couldnt tell which app you meant. Try saying 'open second app' or 'open Restaurant Management App'."


## 9. Phased Implementation Plan

### Phase 1: App-level conversational improvements (high impact)

Scope:
- Small talk and greeting handling.
- `listApps`, `loadApp`, `deleteApp` with index and name resolution.
- Human-friendly error messages.

Tasks:
1. Extend `AiAppGeneratorService`:
   - Ensure `handleSmallTalkIfNeeded` is invoked early in `generateApp`.
   - Add rule-based detection for app-level commands (without GPT).
   - Add helper for resolving app by index/name using `conversationContext.lastAppList` and `AppManager.listApps()`.
2. Introduce a simple Intent Cache component:
   - Provide `getActionForText` and `storeActionForText` methods.
3. Update `GenerationResult` usage:
   - Use `reply` consistently for human messages.
   - Avoid exposing raw internal error strings.
4. Validate with example flows:
   - "Hi, how are you" → small talk only.
   - "show my apps" → list apps.
   - "load second app from the list" → open 2nd app.
   - "load Restaurant Management App" → open by name.

### Phase 2: App creation and template flow

Scope:
- Robust `generateApp` for high-level descriptions.
- Combine GPT-generated designs with template-based fallbacks.

Tasks:
1. Refine prompts in `AiSystemPrompts` for app design.
2. Ensure parsed GPT response maps cleanly to `AppMetadata`, `EntitySchema`, and `PageMeta`.
3. Add summary `reply` describing the generated app in simple language.
4. Add initial rules/cache for common app types (project management, restaurant, CRM, e-commerce).

### Phase 3: Entity and page editing commands

Scope:
- Conversational changes to entities, fields, and pages.

Tasks:
1. Expand action set with entity/page operations.
2. Define rule patterns and cache entries for repeated edit patterns.
3. Use GPT only when a change request is structurally complex.
4. Enhance introspection commands ("what fields does Task have?").

### Phase 4: Optimization and learning

Scope:
- Reduce GPT usage and response time over time.

Tasks:
1. Analyze which prompts and patterns are most frequent and ensure they are covered by rules or cache.
2. Add similarity-based lookup to Intent Cache (beyond exact normalization).
3. Optionally add analytics for action distribution and cache hit rate.


## 10. Success Criteria

- Small talk never triggers backend actions.
- `listApps` / `loadApp` / `deleteApp` work naturally with plain English and context.
- GPT calls are only made for new/complex requests; repeated simple commands are handled locally.
- Users can create non-trivial apps by conversation and refine them iteratively without understanding code.
