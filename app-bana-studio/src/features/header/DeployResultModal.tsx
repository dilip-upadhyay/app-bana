import { useEffect, useId, useRef, useState } from 'react';
import type { DeployResult } from '@appbana/shared';

interface Props {
  appName: string;
  url: string;
  result: DeployResult;
  onClose: () => void;
}

const ENV_STYLE: Record<string, string> = {
  DEV:  'bg-emerald-100 text-emerald-800 border-emerald-200',
  SIT:  'bg-amber-100  text-amber-800  border-amber-200',
  PROD: 'bg-rose-100   text-rose-800   border-rose-200',
};

export function DeployResultModal({ appName, url, result, onClose }: Readonly<Props>) {
  const [copied, setCopied] = useState(false);
  const closeRef = useRef<HTMLButtonElement>(null);
  const urlInputId = useId();

  useEffect(() => {
    closeRef.current?.focus();
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);

  async function copyUrl() {
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      /* clipboard blocked — user can select manually */
    }
  }

  const envClass = ENV_STYLE[result.environment] ?? 'bg-gray-100 text-gray-800 border-gray-200';

  return (
    <output
      aria-live="polite"
      aria-labelledby="deploy-result-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4"
    >
      {/* Backdrop button — dismisses on click/keyboard */}
      <button
        type="button"
        aria-label="Dismiss dialog"
        onClick={onClose}
        className="absolute inset-0 w-full h-full cursor-default focus:outline-none"
        tabIndex={-1}
      />

      {/* Panel */}
      <div className="relative w-full max-w-lg bg-white rounded-2xl shadow-2xl overflow-hidden">
        {/* Success banner */}
        <div className="px-6 py-5 border-b border-gray-100 bg-gradient-to-br from-indigo-50 to-white">
          <div className="flex items-start gap-3">
            <div className="w-10 h-10 rounded-full bg-emerald-100 text-emerald-600 flex items-center justify-center text-xl shrink-0">
              ✓
            </div>
            <div className="flex-1 min-w-0">
              <h2 id="deploy-result-title" className="text-lg font-semibold text-gray-900">
                Deployment successful
              </h2>
              <p className="text-sm text-gray-600 mt-0.5 truncate">
                <span className="font-medium text-gray-800">{appName}</span>
                {' was deployed to '}
                <span className={`inline-block ml-1 px-2 py-0.5 text-xs font-mono rounded border ${envClass}`}>
                  {result.environment}
                </span>
              </p>
            </div>
          </div>
        </div>

        {/* Details */}
        <div className="px-6 py-5 space-y-4">
          {/* Meta grid */}
          <div className="grid grid-cols-3 gap-3 text-xs">
            <div>
              <div className="text-gray-500 uppercase tracking-wide mb-0.5">Version</div>
              <div className="font-mono text-gray-900 font-semibold text-sm">v{result.version}</div>
            </div>
            <div>
              <div className="text-gray-500 uppercase tracking-wide mb-0.5">Duration</div>
              <div className="font-mono text-gray-900 font-semibold text-sm">{result.durationMs} ms</div>
            </div>
            <div>
              <div className="text-gray-500 uppercase tracking-wide mb-0.5">Tables</div>
              <div className="font-mono text-gray-900 font-semibold text-sm">{result.tablesCreated?.length ?? 0}</div>
            </div>
          </div>

          {/* URL block */}
          <div>
            <label htmlFor={urlInputId} className="block text-xs font-medium text-gray-600 mb-1.5">
              Deployed URL
            </label>
            <div className="flex gap-2">
              <input
                id={urlInputId}
                readOnly
                value={url}
                onFocus={(e) => e.currentTarget.select()}
                className="flex-1 min-w-0 px-3 py-2 rounded-md border border-gray-300 bg-gray-50
                           text-xs font-mono text-gray-800
                           focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
              />
              <button
                type="button"
                onClick={copyUrl}
                className={`px-3 py-2 rounded-md text-xs font-medium transition-colors
                  ${copied
                    ? 'bg-emerald-100 text-emerald-700'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'}`}
                title="Copy URL"
              >
                {copied ? 'Copied ✓' : 'Copy'}
              </button>
            </div>
            <p className="text-[11px] text-gray-500 mt-1.5">
              Anyone with an account on this tenant can sign in and use the deployed app.
            </p>
          </div>
        </div>

        {/* Footer actions */}
        <div className="px-6 py-4 border-t border-gray-100 bg-gray-50 flex items-center justify-end gap-2">
          <button
            ref={closeRef}
            type="button"
            onClick={onClose}
            className="px-4 py-2 rounded-md text-sm font-medium text-gray-700 hover:bg-gray-200 transition-colors"
          >
            Close
          </button>
          <a
            href={url}
            target="_blank"
            rel="noopener noreferrer"
            className="px-4 py-2 rounded-md text-sm font-medium bg-indigo-600 text-white
                       hover:bg-indigo-700 shadow-sm transition-colors inline-flex items-center gap-1.5"
          >
            <span>Open app</span>
            <span aria-hidden="true">↗</span>
          </a>
        </div>
      </div>
    </output>
  );
}
