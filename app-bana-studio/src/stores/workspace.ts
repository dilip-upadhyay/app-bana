import { create } from 'zustand';
import type { AppMeta, TenantBranding } from '@appbana/shared';

interface WorkspaceState {
  apps: AppMeta[];
  currentApp: AppMeta | null;
  branding: TenantBranding | null;
  previewRefreshToken: number;
  setApps: (apps: AppMeta[]) => void;
  setCurrentApp: (app: AppMeta | null) => void;
  setBranding: (b: TenantBranding) => void;
  refreshPreview: () => void;
  /**
   * S2.8: clears apps/currentApp/branding. Must be called on every session
   * boundary (explicit sign-out, or the appbana:auth:expired recovery path in
   * AuthGate) so the app switcher never renders a previous session/tenant's
   * app list or selection as if the server had confirmed it for whoever is
   * newly authenticated -- it always waits for a fresh, server-filtered
   * response instead of assuming stale client state still applies.
   */
  resetWorkspace: () => void;
}

export const useWorkspaceStore = create<WorkspaceState>()((set) => ({
  apps: [],
  currentApp: null,
  branding: null,
  previewRefreshToken: 0,
  setApps: (apps) => set({ apps }),
  setCurrentApp: (app) => set({ currentApp: app }),
  setBranding: (branding) => set({ branding }),
  refreshPreview: () => set((s) => ({ previewRefreshToken: s.previewRefreshToken + 1 })),
  resetWorkspace: () => set({ apps: [], currentApp: null, branding: null }),
}));
