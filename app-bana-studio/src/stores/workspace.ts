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
}));
