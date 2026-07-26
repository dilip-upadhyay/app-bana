/**
 * Phase B3 — File upload field.
 *
 * Renders a drag-and-drop dropzone that accepts a single file, base64-encodes
 * it in-browser, POSTs to /api/files/upload, and writes the returned fileId
 * into a hidden <input name={name}> so the parent form submits it as the
 * field's value (matches the shape produced by the `file` sqlType — a
 * VARCHAR(64) column that stores the fileId).
 *
 * We deliberately avoid any external drop-zone dependency: React's built-in
 * drag events + a hidden <input type="file"> keep the runtime slim.
 */
import { useEffect, useRef, useState } from 'react';
import { resolveAppContext } from '@appbana/shared';
import { toast } from './Toaster';

export interface FileUploadFieldProps {
  readonly id: string;
  readonly name: string;
  readonly required?: boolean;
  readonly defaultValue?: string;   // existing fileId (edit mode)
  readonly tenantId?: string;
  readonly appId?: string;
  readonly entityKey?: string;
  readonly fieldName?: string;
  readonly maxSizeBytes?: number;
  readonly acceptedMimeTypes?: readonly string[];
  readonly className?: string;
}

interface UploadedInfo {
  fileId: string;
  filename?: string;
  mimeType?: string;
  size?: number;
}

const DEFAULT_MAX = 10 * 1024 * 1024;

export function FileUploadField(props: Readonly<FileUploadFieldProps>) {
  const {
    id,
    name,
    required,
    defaultValue,
    tenantId,
    appId,
    entityKey,
    fieldName,
    maxSizeBytes = DEFAULT_MAX,
    acceptedMimeTypes,
    className,
  } = props;

  const inputRef = useRef<HTMLInputElement | null>(null);
  const [uploaded, setUploaded] = useState<UploadedInfo | null>(
    defaultValue ? { fileId: defaultValue } : null
  );
  const [uploading, setUploading] = useState(false);
  const [dragOver, setDragOver] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Sync hidden input value on mount / change so `new FormData(form)` picks it up.
  useEffect(() => {
    if (inputRef.current) inputRef.current.value = uploaded?.fileId ?? '';
  }, [uploaded]);

  async function upload(file: File) {
    setError(null);
    if (file.size > maxSizeBytes) {
      const msg = `File exceeds max size (${Math.round(maxSizeBytes / 1024)} KB)`;
      setError(msg);
      toast.error(msg);
      return;
    }
    if (acceptedMimeTypes && acceptedMimeTypes.length > 0) {
      const ok = acceptedMimeTypes.some((m) => {
        if (m.endsWith('/*')) return file.type.startsWith(m.slice(0, -1));
        return file.type === m;
      });
      if (!ok) {
        const msg = `File type "${file.type}" is not allowed`;
        setError(msg);
        toast.error(msg);
        return;
      }
    }

    setUploading(true);
    try {
      const ctx = resolveAppContext(window.location);
      const effectiveTenant = tenantId ?? ctx?.tenantId ?? 'default';
      const effectiveApp = appId ?? ctx?.appId ?? '';
      const base64 = await fileToBase64(file);
      const res = await fetch('/api/files/upload', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          tenantId: effectiveTenant,
          appId: effectiveApp,
          entityKey,
          fieldName: fieldName ?? name,
          filename: file.name,
          mimeType: file.type || 'application/octet-stream',
          contentBase64: base64,
        }),
      });
      if (!res.ok) {
        const bodyText = await res.text().catch(() => '');
        throw new Error(`Upload failed (${res.status}): ${bodyText}`);
      }
      const body = (await res.json()) as UploadedInfo;
      setUploaded(body);
      toast.success('File uploaded', { description: file.name });
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setError(msg);
      toast.error('Upload failed', { description: msg });
    } finally {
      setUploading(false);
    }
  }

  function onDrop(ev: React.DragEvent<HTMLButtonElement>) {
    ev.preventDefault();
    setDragOver(false);
    const file = ev.dataTransfer.files?.[0];
    if (file) void upload(file);
  }

  return (
    <div className={`appbana-file-field ${className ?? ''}`}>
      <input
        ref={inputRef}
        id={id}
        name={name}
        type="hidden"
        defaultValue={defaultValue ?? ''}
        required={required}
      />
      <button
        type="button"
        onClick={() => document.getElementById(`${id}-picker`)?.click()}
        onDragOver={(e) => {
          e.preventDefault();
          setDragOver(true);
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={onDrop}
        disabled={uploading}
        className={[
          'w-full rounded-xl border-2 border-dashed px-4 py-6 text-sm transition-colors',
          dragOver ? 'border-indigo-500 bg-indigo-50' : 'border-slate-300 bg-slate-50',
          uploading ? 'opacity-60 cursor-progress' : 'hover:border-indigo-400 cursor-pointer',
          'text-slate-700',
        ].join(' ')}
      >
        {(() => {
          if (uploading) return <span>Uploading…</span>;
          if (uploaded) {
            return (
              <span>
                <strong>Attached:</strong> {uploaded.filename ?? uploaded.fileId}
                {' · '}
                <span className="text-indigo-600 underline">Replace</span>
              </span>
            );
          }
          return (
            <span>
              <span className="text-indigo-600 underline">Choose a file</span> or drag and drop
            </span>
          );
        })()}
      </button>
      <input
        id={`${id}-picker`}
        type="file"
        accept={acceptedMimeTypes?.join(',')}
        className="sr-only"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) void upload(file);
          // reset so re-selecting the same file re-fires
          e.target.value = '';
        }}
      />
      {uploaded && (
        <div className="mt-2 text-xs text-slate-500">
          Preview:{' '}
          <a
            href={`/api/files/${uploaded.fileId}`}
            target="_blank"
            rel="noopener noreferrer"
            className="text-indigo-600 hover:underline"
          >
            {uploaded.filename ?? 'Open'}
          </a>
        </div>
      )}
      {error && <div className="mt-2 text-xs text-rose-600">{error}</div>}
    </div>
  );
}

function fileToBase64(file: File): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error('Failed to read file'));
    reader.onload = () => {
      const result = reader.result;
      if (typeof result !== 'string') {
        reject(new Error('Unexpected FileReader result'));
        return;
      }
      const comma = result.indexOf(',');
      resolve(comma >= 0 ? result.slice(comma + 1) : result);
    };
    reader.readAsDataURL(file);
  });
}
