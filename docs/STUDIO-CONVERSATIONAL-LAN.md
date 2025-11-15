# Studio Conversational LAN

**Purpose**: Document the long-term architecture needed to make Studio feel like the world’s friendliest, most capable AI builder—one that greets users, reasons about what to build, and orchestrates metadata generation transparently.

## 1. Conversational Orchestration Layer

- **Finite-state intent router**: Drive `AiChatBuilder` with explicit states (`greeting`, `idea-suggest`, `clarification`, `action`, `follow-up`). This keeps friendly responses separate from backend generation calls.
- **Utterance heuristics**: Preprocess user text for greetings, idea requests, and simple directives (list apps, load app, delete app) so the builder responds immediately with human tone before invoking heavy AI.
- **Memory cache**: Track recent apps, preferred themes, and unanswered follow-up questions so the assistant can say “Welcome back to CRM builder” or pick up mid-conversation.

## 2. AI Experience Services

- **AiPersonaService**: Encapsulates persona traits (tone, friendliness level, caution) and exposes composable prompts. Allows toggling between proactive suggestions and detailed technical instructions.
- **AiIntentService**: Normalizes requests into structured intents (`build`, `edit`, `inspect`, `list`, `delete`) before hitting `/api/ai/generate`. Keeps the backend focused on metadata while the client handles experience flow.
- **AiActionService**: Handles manual actions (create/delete/load) with confirmation modals, toasts, and telemetry so the builder behaves like a human operator.

## 3. Metadata Decision Gateway

- **Persona-driven templates**: Maintain a catalog (linked to `builder-database/02-components.json` and new docs) describing recommended templates for concept prompts (e.g., “client success portal”). Enables the assistant to proactively suggest apps with the correct metadata structure.
- **Decision engine**: Takes intent + context (entities, pages, current app) and outputs next steps (generate entities, ask follow-up, render preview). Should be abstracted so future services can plug in new capabilities (e.g., plugin for advanced analytics tables).

## 4. Runtime Telemetry + Feedback

- **Conversation analytics**: Record greeting/idea/resolution transitions so we can iterate on the persona (e.g., 90% of users respond positively to two suggestions). Stores metrics in AppStore or telemetry service.
- **Guided signals**: Use toasts/snackbars (see `StudioTableLive` toasts) to narrate state transitions (“Analyzing your requirements…”, “Here’s what I’ll build next”). Helps users trust the assistant.

## 5. Implementation Touchpoints

- Update `AiChatBuilder.ts` with the new orchestrator states, heuristics, and persona prompts.
- Expand `builder-database/02-components.json` to detail conversational metadata clues (e.g., prompts, persona states) so AI agents understand the builder’s policy.
- Reference this LAN inside `docs/01-ARCHITECTURE.md` under `Studio Builder Architecture` so teammates understand the conversational stack.
- Embed the ideas in `docs/TABLE-LIVE-ENHANCEMENTS.md` when describing how the table component responds to persona cues and toasts.

## 6. Next Steps

1. Prototype the persona services and decision gateway as lightweight helpers within `AiChatBuilder`.
2. Codify conversation telemetry events (greeting received, idea selected, app generated) and log them via AppStore or analytics.
3. Update builder docs and AI prompts referencing this LAN before rolling out the new experience to the Studio runtime.