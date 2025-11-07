import './builder/components/BuilderShell';
import './builder/components/BuilderCanvas';
import './builder/components/BuilderInspector';
import { registerBuiltInAdapters } from './core/adapter-bootstrap';

// Register universal datasource adapters
registerBuiltInAdapters();

// Bootstrap the Studio Builder Shell
const root = document.getElementById('studio-root');
if (root) {
  root.innerHTML = '';
  const shell = document.createElement('studio-builder-shell');
  root.appendChild(shell);
}
