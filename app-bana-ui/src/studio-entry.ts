import './components/AuthGuard';
import './builder/components/BuilderShell';
import './builder/components/BuilderCanvas';
import './builder/components/BuilderInspector';
import { registerBuiltInAdapters } from './core/adapter-bootstrap';
import { setupApiClient } from './core/api-setup';

// Setup API client with authentication interceptors
setupApiClient({
  enableLogging: true,
  enableRetry: true,
});

// Register universal datasource adapters
registerBuiltInAdapters();

// Bootstrap the Studio with Authentication Guard
const root = document.getElementById('studio-root');
if (root) {
  root.innerHTML = '';
  
  // Create auth guard wrapper
  const authGuard = document.createElement('auth-guard');
  
  // Create shell inside auth guard
  const shell = document.createElement('appbana-builder-shell');
  authGuard.appendChild(shell);
  
  // Listen for auth changes
  authGuard.addEventListener('auth-change', (e: Event) => {
    const customEvent = e as CustomEvent;
    if (customEvent.detail.authenticated) {
      console.log('User authenticated, Studio ready');
    }
  });
  
  root.appendChild(authGuard);
}
