import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AppRuntimeShell } from './runtime/AppRuntimeShell';

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* /run/:tenantId/:appId — the deployed app view */}
        <Route path="/run/:tenantId/:appId" element={<AppRuntimeShell />} />
        {/* Root: friendly landing */}
        <Route
          path="*"
          element={
            <div className="flex items-center justify-center min-h-screen bg-gray-50 text-gray-400 text-sm">
              Navigate to /run/&lt;tenantId&gt;/&lt;appId&gt; to view an app.
            </div>
          }
        />
      </Routes>
    </BrowserRouter>
  );
}
