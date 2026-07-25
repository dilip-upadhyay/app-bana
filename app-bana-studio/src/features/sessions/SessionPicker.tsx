import { useEffect, useState } from 'react';
import {
  listSessions,
  getSessionHistory,
  renameSession as apiRenameSession,
  deleteSession as apiDeleteSession,
  type ChatSession,
} from '@appbana/shared';
import { useSessionStore } from '../../stores/session';
import { useWorkspaceStore } from '../../stores/workspace';
import { useChatStore } from '../../stores/chat';
import { useDrawerStore } from '../../stores/drawer';

/**
 * Session picker dropdown — shows recent chat sessions for the current user,
 * optionally filtered to the current app. Supports search, hydrate, rename,
 * and soft-delete.
 */
export function SessionPicker() {
  const { token, userId } = useSessionStore();
  const { currentApp } = useWorkspaceStore();
  const { loadHistory, clearMessages } = useChatStore();
  const { sessionsOpen, closeAll } = useDrawerStore();

  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);

  useEffect(() => {
    if (!sessionsOpen || !token || !userId) return;
    setLoading(true);
    listSessions(userId, token, { appId: currentApp?.id, limit: 50 })
      .then(setSessions)
      .catch(() => setSessions([]))
      .finally(() => setLoading(false));
  }, [sessionsOpen, token, userId, currentApp?.id]);

  async function handleOpen(s: ChatSession) {
    if (!token || !userId) return;
    setBusyId(s.sessionId);
    try {
      const history = await getSessionHistory(userId, s.sessionId, token);
      const msgs = history
        .filter((m) => m.role === 'user' || m.role === 'assistant')
        .map((m) => ({ role: m.role, content: m.content }));
      loadHistory(s.sessionId, msgs);
      closeAll();
    } catch {
      alert('Failed to load session');
    } finally {
      setBusyId(null);
    }
  }

  async function handleRename(s: ChatSession, e: React.MouseEvent) {
    e.stopPropagation();
    if (!token || !userId) return;
    const next = prompt('Rename session:', s.title ?? '');
    if (!next?.trim() || next.trim() === s.title) return;
    try {
      await apiRenameSession(s.sessionId, userId, next.trim(), token);
      setSessions((cur) => cur.map((x) => (x.sessionId === s.sessionId ? { ...x, title: next.trim() } : x)));
    } catch {
      alert('Rename failed');
    }
  }

  async function handleDelete(s: ChatSession, e: React.MouseEvent) {
    e.stopPropagation();
    if (!token || !userId) return;
    if (!confirm(`Delete session "${s.title ?? s.sessionId.slice(0, 8)}"? This cannot be undone from the UI.`)) return;
    try {
      await apiDeleteSession(s.sessionId, userId, token);
      setSessions((cur) => cur.filter((x) => x.sessionId !== s.sessionId));
    } catch {
      alert('Delete failed');
    }
  }

  function handleNewSession() {
    clearMessages();
    closeAll();
  }

  if (!sessionsOpen) return null;

  const filtered = search.trim()
    ? sessions.filter((s) => (s.title ?? '').toLowerCase().includes(search.trim().toLowerCase()))
    : sessions;

  return (
    <div
      className="absolute top-full right-0 mt-1 w-80 bg-gray-800 border border-gray-700 rounded-lg
                 shadow-xl z-50 text-sm flex flex-col max-h-[70vh]"
    >
      <div className="p-2 border-b border-gray-700 flex items-center gap-2">
        <input
          autoFocus
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search sessions…"
          className="flex-1 bg-gray-900 text-white text-xs px-2 py-1.5 rounded border border-gray-700
                     focus:border-indigo-500 focus:outline-none placeholder-gray-500"
        />
        <button
          onClick={handleNewSession}
          title="New session"
          className="text-xs bg-indigo-600 hover:bg-indigo-500 text-white px-2 py-1.5 rounded"
        >
          + New
        </button>
      </div>

      <div className="flex-1 overflow-y-auto py-1">
        {loading && <div className="px-3 py-4 text-gray-500 text-xs text-center">Loading…</div>}
        {!loading && filtered.length === 0 && (
          <div className="px-3 py-6 text-gray-500 text-xs text-center">
            {sessions.length === 0 ? 'No sessions yet.' : 'No matches.'}
          </div>
        )}
        {filtered.map((s) => (
          <div
            key={s.sessionId}
            role="button"
            tabIndex={0}
            onClick={() => handleOpen(s)}
            onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); handleOpen(s); } }}
            className="group px-3 py-2 hover:bg-gray-700 cursor-pointer flex items-start gap-2
                       border-l-2 border-transparent hover:border-indigo-500 focus:outline-none
                       focus:border-indigo-500"
          >
            <div className="flex-1 min-w-0">
              <div className="text-gray-200 truncate">{s.title ?? '(untitled)'}</div>
              <div className="text-[10px] text-gray-500 flex gap-2 mt-0.5">
                <span>{s.turnCount ?? 0} turns</span>
                {s.lastActivity && <span>· {formatRelative(s.lastActivity)}</span>}
                {s.appId && !currentApp && <span className="truncate">· {s.appId.slice(0, 8)}</span>}
              </div>
            </div>
            <div className="opacity-0 group-hover:opacity-100 flex gap-1 shrink-0 transition-opacity">
              <button
                onClick={(e) => handleRename(s, e)}
                disabled={busyId === s.sessionId}
                title="Rename"
                className="text-gray-400 hover:text-white px-1.5 py-0.5 rounded hover:bg-gray-600 text-xs"
              >
                ✎
              </button>
              <button
                onClick={(e) => handleDelete(s, e)}
                disabled={busyId === s.sessionId}
                title="Delete"
                className="text-red-400 hover:text-red-300 px-1.5 py-0.5 rounded hover:bg-gray-600 text-xs"
              >
                🗑
              </button>
            </div>
          </div>
        ))}
      </div>

      {currentApp && (
        <div className="p-2 border-t border-gray-700 text-[10px] text-gray-500 text-center">
          Showing sessions for <span className="text-gray-300">{currentApp.name}</span>
        </div>
      )}
    </div>
  );
}

function formatRelative(ms: number): string {
  const diff = Date.now() - ms;
  const min = Math.floor(diff / 60000);
  if (min < 1) return 'just now';
  if (min < 60) return `${min}m ago`;
  const hr = Math.floor(min / 60);
  if (hr < 24) return `${hr}h ago`;
  const d = Math.floor(hr / 24);
  if (d < 30) return `${d}d ago`;
  return new Date(ms).toLocaleDateString();
}
