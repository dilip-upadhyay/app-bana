import { Header } from './features/header/Header';
import { ChatPane } from './features/chat/ChatPane';
import { PreviewPane } from './features/preview/PreviewPane';
import { AuthGate } from './features/auth/AuthGate';
import { DataDrawer } from './features/data-drawer/DataDrawer';

export function App() {
  return (
    <AuthGate>
      <div className="flex flex-col h-screen overflow-hidden">
        <Header />
        <div className="flex flex-1 overflow-hidden">
          {/* Chat — 420px fixed, scrolls internally */}
          <div className="w-[420px] shrink-0 border-r border-gray-800 flex flex-col overflow-hidden">
            <ChatPane />
          </div>
          {/* Preview — fills remaining space */}
          <div className="flex-1 overflow-hidden">
            <PreviewPane />
          </div>
        </div>
        {/* Slide-in drawers */}
        <DataDrawer />
      </div>
    </AuthGate>
  );
}
