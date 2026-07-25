import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface SessionState {
  token: string | null;
  userId: string | null;
  email: string | null;
  name: string | null;
  tenantId: string;
  setSession: (s: Omit<SessionState, 'setSession' | 'clearSession' | 'tenantId'> & { tenantId: string }) => void;
  clearSession: () => void;
}

export const useSessionStore = create<SessionState>()(
  persist(
    (set) => ({
      token: null,
      userId: null,
      email: null,
      name: null,
      tenantId: 'default',
      setSession: (s) => set(s),
      clearSession: () => set({ token: null, userId: null, email: null, name: null, tenantId: 'default' }),
    }),
    { name: 'appbana-session' }
  )
);
