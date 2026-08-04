import { useWorkspaceStore } from './workspace';
import { useChatStore } from './chat';

/**
 * Clears every piece of client-side state that must never survive a session
 * boundary (explicit sign-out in Header.tsx, or the appbana:auth:expired
 * recovery path in AuthGate.tsx -- the latter deliberately does NOT reload
 * the page, so nothing unmounts on its own and every in-memory store keeps
 * whatever it was last holding).
 *
 * A single composed action -- rather than every session-boundary call site
 * remembering to reset each store individually -- is deliberate: the S2.8
 * review round found that fixing the app-switcher's workspace state on this
 * exact path still left ChatPane's full conversation history and any
 * pasted-image attachments renderable to whoever authenticates next in the
 * same tab, because nothing had reset useChatStore. Route any future
 * session-scoped store's reset through here too, instead of adding another
 * call site-by-call-site wire-up.
 */
export function resetSessionScopedState() {
  useWorkspaceStore.getState().resetWorkspace();
  // Abort any in-flight generation before wiping messages, so a lingering
  // fetch for the departing session doesn't keep streaming pointlessly.
  useChatStore.getState().stopStreaming();
  useChatStore.getState().clearMessages();
}
