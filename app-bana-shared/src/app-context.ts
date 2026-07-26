// Resolves tenant + app IDs from the current URL.
// Path strategy: /run/:tenantId/:appId
// Hostname strategy (Stage 5): {tenantId}.apps.appbana.com/app/:appId
//
// Import from @appbana/shared and call resolveAppContext(window.location)
// so the resolution strategy can be swapped in one place.

export interface AppContextRef {
  tenantId: string;
  appId: string;
}

export function resolveAppContext(location: { pathname: string; hostname: string }): AppContextRef | null {
  // Path-based: /run/:tenantId/:appId
  const match = /^\/run\/([^/]+)\/([^/]+)/.exec(location.pathname);
  if (match) {
    return { tenantId: match[1], appId: match[2] };
  }
  return null;
}
