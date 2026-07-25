import { useEffect, useRef, useState } from 'react';
import { streamAgentChat } from '@appbana/shared';
import { useSessionStore } from '../../stores/session';
import { useWorkspaceStore } from '../../stores/workspace';
import { useChatStore } from '../../stores/chat';
import { MessageBubble } from './MessageBubble';

const SUGGESTIONS_IDLE = [
  'Describe your app idea…',
  'Try: "I want a contact list app"',
  'Try: "Build me an inventory tracker"',
  'Try: "Create a CRM for my sales team"',
];

const SUGGESTIONS_ACTIVE = [
  'Seed sample data',
  'Add another page',
  'Deploy my app',
  'Add more entities',
];

export function ChatPane() {
  const { token, tenantId, userId } = useSessionStore();
  const { currentApp, refreshPreview } = useWorkspaceStore();
  const {
    messages, sessionId, streaming,
    addUserMessage, startAssistantMessage, applyEvent, finalizeMessage,
    setStreaming, stopStreaming, setSessionId,
  } = useChatStore();

  const [draft, setDraft] = useState('');
  const bottomRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  async function sendMessage() {
    const text = draft.trim();
    if (!text || streaming) return;
    setDraft('');

    addUserMessage(text);
    const assistantId = startAssistantMessage();

    const ctrl = new AbortController();
    setStreaming(true, ctrl);

    try {
      const stream = streamAgentChat(
        {
          message: text,
          sessionId,
          userId: userId ?? 'anonymous',
          tenantId: tenantId ?? 'default',
          appId: currentApp?.id ?? 'default',
          token: token ?? undefined,
        },
        ctrl.signal
      );

      for await (const ev of stream) {
        applyEvent(assistantId, ev);
        // After a successful scaffold or page creation, refresh the preview
        if (ev.event === 'tool_call_end' && ev.data.status === 'ok') {
          refreshPreview();
        }
        if (ev.event === 'done' && ev.data.conversationId) {
          setSessionId(ev.data.conversationId);
        }
      }
    } catch (err) {
      if (!(err instanceof DOMException && err.name === 'AbortError')) {
        applyEvent(assistantId, {
          event: 'token',
          data: { text: '\n\n_Sorry, I encountered an error. Please try again._' },
        });
      }
    } finally {
      finalizeMessage(assistantId);
      setStreaming(false, null);
    }
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendMessage();
    }
  }

  const hasMeaningfulMessages = messages.some((m) => m.role === 'user');
  const suggestions = hasMeaningfulMessages ? SUGGESTIONS_ACTIVE : SUGGESTIONS_IDLE.slice(1);

  return (
    <div className="flex flex-col h-full bg-gray-950">
      {/* Message list */}
      <div className="flex-1 overflow-y-auto px-4 py-4 space-y-1">
        {messages.length === 0 && (
          <div className="flex flex-col items-center justify-center h-full gap-4 text-center">
            <span className="text-5xl">🍌</span>
            <h2 className="text-xl font-semibold text-white">AppBana AI Builder</h2>
            <p className="text-gray-400 text-sm max-w-xs">
              Describe your app in plain language and I'll build it for you — database, API, and UI.
            </p>
          </div>
        )}
        {messages.map((m) => <MessageBubble key={m.id} msg={m} />)}
        <div ref={bottomRef} />
      </div>

      {/* Suggestion chips */}
      {!streaming && (
        <div className="px-4 pb-2 flex gap-2 flex-wrap">
          {suggestions.slice(0, 3).map((s) => (
            <button
              key={s}
              onClick={() => { setDraft(s.startsWith('Try:') ? s.replace('Try: "', '').replace('"', '') : s); textareaRef.current?.focus(); }}
              className="text-xs bg-gray-800 hover:bg-gray-700 text-gray-300 px-3 py-1.5
                         rounded-full border border-gray-700 transition-colors"
            >
              {s.replace('Try: ', '')}
            </button>
          ))}
        </div>
      )}

      {/* Composer */}
      <div className="border-t border-gray-800 p-3">
        <div className="flex items-end gap-2 bg-gray-800 rounded-xl border border-gray-700 px-3 py-2">
          <textarea
            ref={textareaRef}
            rows={1}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Describe your app or ask anything…"
            className="flex-1 bg-transparent text-sm text-white placeholder-gray-500 resize-none
                       focus:outline-none max-h-40 leading-relaxed"
            style={{ height: 'auto' }}
            onInput={(e) => {
              const t = e.currentTarget;
              t.style.height = 'auto';
              t.style.height = `${t.scrollHeight}px`;
            }}
          />
          {streaming ? (
            <button
              onClick={stopStreaming}
              className="shrink-0 w-8 h-8 bg-red-600 hover:bg-red-500 rounded-lg flex items-center
                         justify-center text-white transition-colors"
              title="Stop"
            >
              ■
            </button>
          ) : (
            <button
              onClick={sendMessage}
              disabled={!draft.trim()}
              className="shrink-0 w-8 h-8 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-40
                         rounded-lg flex items-center justify-center text-white transition-colors"
              title="Send (Enter)"
            >
              ➤
            </button>
          )}
        </div>
        <p className="text-xs text-gray-600 mt-1 pl-1">↵ Send · ⇧↵ Newline</p>
      </div>
    </div>
  );
}
