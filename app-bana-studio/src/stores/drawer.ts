import { create } from 'zustand';

interface DrawerState {
  dataOpen: boolean;
  sessionsOpen: boolean;
  toggleData: () => void;
  toggleSessions: () => void;
  closeAll: () => void;
}

/**
 * UI-only state for slide-in drawers/popovers in the studio.
 * Kept separate from workspace/session state so the header + drawers can
 * communicate without prop drilling.
 */
export const useDrawerStore = create<DrawerState>()((set) => ({
  dataOpen: false,
  sessionsOpen: false,
  toggleData: () => set((s) => ({ dataOpen: !s.dataOpen, sessionsOpen: false })),
  toggleSessions: () => set((s) => ({ sessionsOpen: !s.sessionsOpen, dataOpen: false })),
  closeAll: () => set({ dataOpen: false, sessionsOpen: false }),
}));
