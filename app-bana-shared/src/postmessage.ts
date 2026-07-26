// postMessage contract between studio and runtime.
// Both sides import this type so the protocol never drifts.

export type AppBanaPostMessage =
  // Runtime → Studio
  | { type: 'ready' }
  | { type: 'selection'; nodeId: string; pageId: string; entity?: string; field?: string; bbox?: DOMRect }
  | { type: 'hover'; nodeId: string; pageId: string; entity?: string; field?: string }
  | { type: 'error'; message: string }
  // Studio → Runtime
  | { type: 'token'; jwt: string; userId?: string; email?: string; name?: string; tenantId?: string }
  | { type: 'setMode'; mode: 'browse' | 'inspect' }
  | { type: 'setPage'; pageId: string }
  | { type: 'highlight'; nodeId: string }
  | { type: 'reload' };

export type RuntimeMode = 'browse' | 'inspect';
