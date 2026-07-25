import { create } from 'zustand';
import type { SseEvent } from '@appbana/shared';

export interface ToolCall {
  id: string;
  name: string;
  args: unknown;
  status: 'running' | 'ok' | 'error';
  result?: unknown;
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  toolCalls?: ToolCall[];
  streaming?: boolean;
}

interface ChatState {
  messages: ChatMessage[];
  sessionId: string;
  conversationState: string;
  streaming: boolean;
  abortController: AbortController | null;
  addUserMessage: (text: string) => string;
  startAssistantMessage: () => string;
  applyEvent: (msgId: string, ev: SseEvent) => void;
  finalizeMessage: (msgId: string) => void;
  setStreaming: (v: boolean, ctrl?: AbortController | null) => void;
  setSessionId: (id: string) => void;
  stopStreaming: () => void;
  clearMessages: () => void;
}

let seq = 0;
const uid = () => `msg-${Date.now()}-${seq++}`;

export const useChatStore = create<ChatState>()((set, get) => ({
  messages: [],
  sessionId: crypto.randomUUID(),
  conversationState: 'GREETING',
  streaming: false,
  abortController: null,

  addUserMessage: (text) => {
    const id = uid();
    set((s) => ({ messages: [...s.messages, { id, role: 'user', content: text }] }));
    return id;
  },

  startAssistantMessage: () => {
    const id = uid();
    set((s) => ({ messages: [...s.messages, { id, role: 'assistant', content: '', toolCalls: [], streaming: true }] }));
    return id;
  },

  applyEvent: (msgId, ev) => {
    set((s) => ({
      messages: s.messages.map((m) => {
        if (m.id !== msgId) return m;
        switch (ev.event) {
          case 'state':
            return m; // state is global, handled separately
          case 'token':
            return { ...m, content: m.content + ev.data.text };
          case 'tool_call_start': {
            const tc: ToolCall = { id: ev.data.id, name: ev.data.name, args: ev.data.args, status: 'running' };
            return { ...m, toolCalls: [...(m.toolCalls ?? []), tc] };
          }
          case 'tool_call_end':
            return {
              ...m,
              toolCalls: (m.toolCalls ?? []).map((tc) =>
                tc.id === ev.data.id ? { ...tc, status: ev.data.status, result: ev.data.result } : tc
              ),
            };
          default:
            return m;
        }
      }),
      ...(ev.event === 'state' ? { conversationState: ev.data.conversationState } : {}),
    }));
  },

  finalizeMessage: (msgId) => {
    set((s) => ({
      messages: s.messages.map((m) => (m.id === msgId ? { ...m, streaming: false } : m)),
    }));
  },

  setStreaming: (v, ctrl) => set({ streaming: v, abortController: ctrl ?? null }),

  setSessionId: (id) => set({ sessionId: id }),

  stopStreaming: () => {
    get().abortController?.abort();
    set((s) => ({
      streaming: false,
      abortController: null,
      messages: s.messages.map((m) => (m.streaming ? { ...m, streaming: false } : m)),
    }));
  },

  clearMessages: () => set({ messages: [], sessionId: crypto.randomUUID() }),
}));
