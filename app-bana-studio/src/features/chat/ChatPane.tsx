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
    messages, sessionId, streaming, attachments,
    addUserMessage, startAssistantMessage, applyEvent, finalizeMessage,
    setStreaming, stopStreaming, setSessionId,
    addAttachment, removeAttachment, clearAttachments,
  } = useChatStore();

  const [draft, setDraft] = useState('');
  const bottomRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // Cross-component composer prefill (used by DataDrawer "Ask AI to seed")
  useEffect(() => {
    const handler = (e: Event) => {
      const detail = (e as CustomEvent<string>).detail;
      if (typeof detail === 'string') {
        setDraft(detail);
        textareaRef.current?.focus();
      }
    };
    window.addEventListener('studio:composer:set', handler);
    return () => window.removeEventListener('studio:composer:set', handler);
  }, []);

  async function sendMessage() {
    const text = draft.trim();
    if ((!text && attachments.length === 0) || streaming) return;
    setDraft('');
    const images = [...attachments];
    clearAttachments();

    addUserMessage(text || '(image)');
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
          appName: currentApp?.name ?? '',
          token: token ?? undefined,
          ...(images.length > 0 ? { images } : {}),
        },
        ctrl.signal
      );

      let sawToken = false;
      for await (const ev of stream) {
        applyEvent(assistantId, ev);
        if (ev.event === 'token') sawToken = true;
        // After a successful scaffold or page creation, refresh the preview
        if (ev.event === 'tool_call_end' && ev.data.status === 'ok') {
          refreshPreview();
        }
        if (ev.event === 'auth_expired') {
          // C4.4e Review #12 — a tool inside this already-open (200) stream hit a backend 401.
          // The transport-level `appbana:auth:expired` recovery (dispatched by authedFetch on an
          // *outer* 401) can never fire for this case, so dispatch the same event manually and
          // let AuthGate.tsx's existing listener handle the rest with zero changes there.
          window.dispatchEvent(new CustomEvent('appbana:auth:expired'));
        }
        if (ev.event === 'done') {
          if (ev.data.conversationId) setSessionId(ev.data.conversationId);
          // Safety net: if the backend never sent a token but has a finalMessage,
          // surface it so the assistant bubble isn't empty.
          if (!sawToken && ev.data.finalMessage) {
            applyEvent(assistantId, { event: 'token', data: { text: ev.data.finalMessage } });
          }
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

  function handlePaste(e: React.ClipboardEvent<HTMLTextAreaElement>) {
    const items = e.clipboardData?.items;
    if (!items) return;
    for (const item of items) {
      if (item.kind === 'file' && item.type.startsWith('image/')) {
        const file = item.getAsFile();
        if (!file) continue;
        if (file.size > 5 * 1024 * 1024) {
          alert('Image too large (max 5 MB)');
          continue;
        }
        e.preventDefault();
        const reader = new FileReader();
        reader.onload = () => {
          const dataUrl = reader.result;
          if (typeof dataUrl === 'string') addAttachment(dataUrl);
        };
        reader.readAsDataURL(file);
      }
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
            <img src="/logo.svg" alt="AppBana" className="h-20 w-20 object-contain" draggable={false} />
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
        {/* Attachment thumbnails */}
        {attachments.length > 0 && (
          <div className="mb-2 flex flex-wrap gap-2">
            {attachments.map((src, i) => (
              <div key={src.slice(-32) + i} className="relative group">
                <img
                  src={src}
                  alt={`Attachment ${i + 1}`}
                  className="w-14 h-14 object-cover rounded border border-gray-700"
                />
                <button
                  onClick={() => removeAttachment(i)}
                  aria-label="Remove attachment"
                  className="absolute -top-1.5 -right-1.5 w-5 h-5 bg-gray-900 border border-gray-600
                             rounded-full flex items-center justify-center text-white text-xs
                             opacity-0 group-hover:opacity-100 hover:bg-red-600 transition-opacity"
                >
                  ×
                </button>
              </div>
            ))}
          </div>
        )}
        <div className="flex items-end gap-2 bg-gray-800 rounded-xl border border-gray-700 px-3 py-2">
          <textarea
            ref={textareaRef}
            rows={1}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={handleKeyDown}
            onPaste={handlePaste}
            placeholder="Describe your app or ask anything… (paste an image to attach)"
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
              disabled={!draft.trim() && attachments.length === 0}
              className="shrink-0 w-8 h-8 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-40
                         rounded-lg flex items-center justify-center text-white transition-colors"
              title="Send (Enter)"
            >
              ➤
            </button>
          )}
        </div>
        <p className="text-xs text-gray-600 mt-1 pl-1">↵ Send · ⇧↵ Newline · 📋 Paste images</p>
      </div>
    </div>
  );
}
