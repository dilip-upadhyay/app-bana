# AppBana AI Builder - Active Tasks

## Current Epic: Intelligent Dialogue (Epic 3)

### 🚀 Next Up: Story 3.1 - Implement Dialogue Manager
**Goal:** Implement a strict state machine `DialogueManager` into `AiChatController` to orchestrate deterministic phases of the app building process, preventing the `AiAgent` LLM from relying purely on autonomous system prompting.

#### Tasks to Complete:
- [ ] **State Machine Logic**: Enhance `com.appbana.ai.dialogue.DialogueManager` to classify active user intents (`INITIAL`, `GATHERING_INFO`, `CONFIRMING_DETAILS`, `CREATING`).
- [ ] **Controller Integration**: Update `com.appbana.ai.api.AiChatController` to proxy requests through `DialogueManager` state checks *before* delegating execution to the `AiAgent`.
- [ ] **Prompt Trimming**: Modify `AiAgent.java` (`buildAgentPrompt`) to dynamically constrain available tools based on the active `ConversationState`. (e.g., Only expose `scaffold_app` when state is `CREATING`).
- [ ] **Tests**: Create Unit tests covering LLM integration with `DialogueManagerTest.java`.

### 📌 Backlog
- Refine LLM Error Recovery loops (preventing max iteration starvation on failed API calls).
- Enhance Visual Feedback (Vite UI) to show typing indicators while the `AiAgent` executes long-running internal thoughts.
