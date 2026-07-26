/**
 * Renderer.tsx — React port of app-bana-ui/src/runtime/renderer/Renderer.ts
 *
 * Converts a PageMeta node tree into JSX, emitting data-appbana-* attrs on
 * every element so Stage 6 (select-and-instruct) can identify them.
 *
 * Form controls (input / select / textarea) are always wrapped in an
 * `.appbana-field` block with a persistent `<label>` derived from
 * props.label / name / placeholder — so the field's meaning stays visible
 * after the user starts typing.
 */
import { useEffect, useRef, useState } from 'react';
import type { PageMeta, ComponentNode, FieldCondition } from '@appbana/shared';
import { fetchEntityRows, insertEntityRow, ApiFieldError } from '@appbana/shared';
import { StudioTableLive } from './StudioTableLive';
import { qualifyEntityKey, getRuntimeToken } from './qualifyEntityKey';
import { FormActions } from './FormActions';
import { toast } from './Toaster';
import { humanizeHeader } from './cell-formatters';
import { PageShell } from './PageShell';
import { PageActions } from './PageActions';
import { DatePicker } from './DatePicker';
import { Skeleton } from './Skeleton';
import { useEntityFormValidation } from './useEntityFormValidation';
import { EntityFormErrorProvider } from './entity-form-context';
import { FormField, ValidatedInput, ValidatedSelect, ValidatedTextarea } from './FormField';
import { ReferenceCombobox } from './ReferenceCombobox';
import { WizardShell } from './WizardShell';
import { FormValuesProvider } from './form-values-context';
import { ConditionalField } from './ConditionalField';
import { FileUploadField } from './FileUploadField';

// Sprint 3 task 3.7 — anything larger than this switches from native <select>
// to the search-driven combobox. Kept small so real-world lookup tables that
// grow past a couple screens of rows stay usable.
const COMBOBOX_THRESHOLD = 20;

/** Turn "full_name" / "first-name" / "firstName" into "Full name". */
function humanize(raw: string | undefined): string {
  if (!raw) return '';
  return humanizeHeader(raw);
}

/**
 * Classify a page's dominant purpose so we can pick the right shell width
 * and background. Forms sit inside a centred, narrower card; lists fill the
 * viewport with their own card chrome; everything else uses a plain body.
 *
 * Sprint 3 task 3.2 — When the scaffolder wrote an authoritative
 * `PageMeta.kind`, we trust it. Otherwise fall back to the legacy node-tree
 * sniff so pre-3.2 apps keep working. `dashboard` collapses to `other`
 * for shell purposes.
 */
function classifyPage(page: PageMeta): 'form' | 'list' | 'other' {
  if (page.kind === 'form' || page.kind === 'detail') return 'form';
  if (page.kind === 'list') return 'list';
  if (page.kind === 'dashboard') return 'other';
  const nodes = page.nodes ?? [];
  if (nodes.some((n) => n.type === 'form' || n.type === 'studio-form')) return 'form';
  if (nodes.some((n) => n.type === 'table' || n.type === 'grid' || n.type === 'appbana-table-live')) return 'list';
  return 'other';
}

function pageTitle(page: PageMeta): string {
  const raw = page.name ?? page.id ?? 'Page';
  return humanize(raw);
}

function pageSubtitle(page: PageMeta): string | undefined {
  const kind = classifyPage(page);
  if (kind === 'form')  return 'Fill in the details and save.';
  if (kind === 'list')  return 'Browse and manage your records.';
  return undefined;
}

export function renderPage(page: PageMeta): React.ReactElement {
  const nodeMap = new Map(page.nodes.map((n) => [n.id, n]));
  const root = nodeMap.get(page.rootId);
  if (!root) {
    return (
      <PageShell title={pageTitle(page)}>
        <div className="text-rose-600 text-sm p-4 border border-rose-200 rounded-lg bg-rose-50">
          Root node not found: {page.rootId}
        </div>
      </PageShell>
    );
  }

  // Phase B1 — Wizard layout takes over the whole page body. Collect every
  // form-field descendant by `name` so WizardShell can render just the
  // subset belonging to the current step.
  if (page.layout === 'wizard' && Array.isArray(page.steps) && page.steps.length > 0) {
    const fieldByName = collectFormFieldsByName(root, nodeMap, page.id);
    const entity = findFormEntity(root, nodeMap) ?? '';
    return (
      <PageShell
        title={pageTitle(page)}
        subtitle={pageSubtitle(page)}
        actions={<PageActions page={page} />}
      >
        <div className="max-w-3xl w-full mx-auto bg-white rounded-xl border border-slate-200 shadow-sm p-6 sm:p-8">
          <WizardShell
            entity={entity}
            steps={page.steps}
            renderField={(name) => fieldByName.get(name) ?? null}
            draftId={page.id}
          />
        </div>
      </PageShell>
    );
  }

  const kind = classifyPage(page);
  const inner = renderNode(root, nodeMap, page.id);
  // Form pages sit in a centred, narrower card so the eye lands on the fields.
  // List pages let their child table own the card chrome and fill the width.
  const body = kind === 'form'
    ? <div className="max-w-3xl w-full mx-auto bg-white rounded-xl border border-slate-200 shadow-sm p-6 sm:p-8">{inner}</div>
    : inner;
  return (
    <PageShell
      title={pageTitle(page)}
      subtitle={pageSubtitle(page)}
      actions={<PageActions page={page} />}
    >
      {body}
    </PageShell>
  );
}

/** Walk the node tree and return a map { fieldName -> rendered JSX } for
 *  every form control (input / select / textarea / reference). Used by
 *  the wizard path to render fields per-step without invoking the whole
 *  form node.
 */
function collectFormFieldsByName(
  root: ComponentNode,
  nodeMap: Map<string, ComponentNode>,
  pageId: string,
): Map<string, React.ReactElement> {
  const out = new Map<string, React.ReactElement>();
  const walk = (node: ComponentNode) => {
    const props = node.props ?? {};
    const name = String(props.name ?? props.field ?? '');
    const isField = ['input', 'select', 'textarea', 'reference'].includes(node.type);
    if (isField && name && !out.has(name)) {
      out.set(name, renderNode(node, nodeMap, pageId));
    }
    for (const childId of node.children ?? []) {
      const child = nodeMap.get(childId);
      if (child) walk(child);
    }
  };
  walk(root);
  return out;
}

/** Find the entity string on the first `form` / `studio-form` node in the tree. */
function findFormEntity(
  root: ComponentNode,
  nodeMap: Map<string, ComponentNode>,
): string | null {
  const stack: ComponentNode[] = [root];
  while (stack.length) {
    const node = stack.pop()!;
    if (node.type === 'form' || node.type === 'studio-form') {
      const e = String(node.props?.entity ?? '');
      if (e) return e;
    }
    for (const childId of node.children ?? []) {
      const child = nodeMap.get(childId);
      if (child) stack.push(child);
    }
  }
  return null;
}

function fieldLabel(props: Record<string, unknown>): string {
  const label = props.label as string | undefined;
  if (label && String(label).trim()) return String(label);
  const name = props.name as string | undefined;
  if (name && String(name).trim()) return humanize(String(name));
  const placeholder = props.placeholder as string | undefined;
  if (placeholder && String(placeholder).trim()) return String(placeholder);
  return '';
}

/**
 * Phase B2 — Optional per-field condition metadata rides on `node.props`.
 * The scaffolder / AI Builder writes it as a plain object matching the
 * FieldCondition type from @appbana/shared.
 */
function readConditions(props: Record<string, unknown>): FieldCondition | undefined {
  const c = props.conditions;
  if (!c || typeof c !== 'object') return undefined;
  return c as FieldCondition;
}

/** Wrap a field's rendered JSX with a ConditionalField only when the field
 *  actually has conditions — avoids the extra provider read on every input. */
function maybeConditional(props: Record<string, unknown>, jsx: React.ReactElement): React.ReactElement {
  const conditions = readConditions(props);
  if (!conditions) return jsx;
  return <ConditionalField conditions={conditions}>{jsx}</ConditionalField>;
}

function renderNode(
  node: ComponentNode,
  nodeMap: Map<string, ComponentNode>,
  pageId: string
): React.ReactElement {
  const props = node.props ?? {};
  const className = (props.className as string) ?? '';
  const style = (props.style as React.CSSProperties | string) ?? '';
  const styleObj: React.CSSProperties = typeof style === 'string' ? {} : style;

  const dataAttrs = {
    'data-appbana-node': node.id,
    'data-appbana-page': pageId,
  };
  const entityAttr = props.entity ? { 'data-appbana-entity': props.entity as string } : {};
  const fieldAttr  = props.field  ? { 'data-appbana-field':  props.field as string  } : {};

  const children = (node.children ?? []).map((childId) => {
    const child = nodeMap.get(childId);
    return child ? renderNode(child, nodeMap, pageId) : null;
  });

  switch (node.type) {
    case 'text':
      return (
        <p
          key={node.id}
          className={className}
          style={styleObj}
          {...dataAttrs}
        >
          {String(props.content ?? props.text ?? '')}
        </p>
      );

    case 'button':
      // The scaffolder emits a synthetic "Save" button with actionType=save-entity
      // inside every form. As of Sprint 1 of the Runtime UX Overhaul, forms
      // render their own sticky FormActions bar with Save + Save & Add-another,
      // so we suppress the legacy button to avoid a duplicate. Any non-save
      // button (e.g. user-authored) still renders normally.
      if (props.actionType === 'save-entity') {
        return <span key={node.id} hidden aria-hidden="true" {...dataAttrs} />;
      }
      return (
        <button
          key={node.id}
          className={`appbana-button ${className}`}
          style={styleObj}
          {...dataAttrs}
          disabled={Boolean(props.disabled)}
        >
          {String(props.label ?? props.text ?? 'Button')}
        </button>
      );

    case 'reference': {
      const label = fieldLabel(props);
      const required = Boolean(props.required);
      const inputId = `appbana-ref-${node.id}`;
      const refEntity = String(
        props.referenceEntity ?? props.entity ?? props.entityName ?? ''
      );
      const helpText = String(props.help ?? props.description ?? '');
      const fieldName = String(props.name ?? refEntity);
      return maybeConditional(props,
        <FormField
          key={node.id}
          name={fieldName}
          label={label}
          htmlFor={inputId}
          required={required}
          helpText={helpText}
          dataAttrs={dataAttrs}
        >
          <ReferenceField
            id={inputId}
            name={fieldName}
            refEntity={refEntity}
            required={required}
            defaultValue={String(props.value ?? '')}
            className={className}
            styleObj={styleObj}
            entityAttr={entityAttr}
            fieldAttr={fieldAttr}
          />
        </FormField>
      );
    }

    case 'input': {
      const label = fieldLabel(props);
      const required = Boolean(props.required);
      const inputId = `appbana-in-${node.id}`;
      const helpText = String(props.help ?? props.description ?? '');
      const nestedType = String(props.type ?? props.inputType ?? 'text');
      const fieldName = String(props.name ?? '');
      // Phase B3 — file upload dropzone.
      if (nestedType === 'file') {
        const constraints = (props.fileConstraints as { maxSizeBytes?: number; acceptedMimeTypes?: string[] } | undefined) ?? {};
        return maybeConditional(props,
          <FormField
            key={node.id}
            name={fieldName}
            label={label}
            htmlFor={inputId}
            required={required}
            helpText={helpText}
            dataAttrs={dataAttrs}
          >
            <FileUploadField
              id={inputId}
              name={fieldName}
              required={required}
              defaultValue={String(props.value ?? '')}
              entityKey={String(props.entity ?? '')}
              fieldName={fieldName}
              maxSizeBytes={constraints.maxSizeBytes}
              acceptedMimeTypes={constraints.acceptedMimeTypes}
              className={className}
            />
          </FormField>
        );
      }
      // Scaffold emits reference FK fields as {type:'input', props:{type:'reference', field:'Customer'}}.
      // Route those to the live-loaded <select> instead of a plain text input.
      if (nestedType === 'reference') {
        const refEntity = String(
          props.referenceEntity ?? props.field ?? props.name ?? ''
        );
        const refFieldName = String(props.name ?? props.field ?? refEntity);
        return maybeConditional(props,
          <FormField
            key={node.id}
            name={refFieldName}
            label={label}
            htmlFor={inputId}
            required={required}
            helpText={helpText}
            dataAttrs={dataAttrs}
          >
            <ReferenceField
              id={inputId}
              name={refFieldName}
              refEntity={refEntity}
              required={required}
              defaultValue={String(props.value ?? '')}
              className={className}
              styleObj={styleObj}
              entityAttr={entityAttr}
              fieldAttr={fieldAttr}
            />
          </FormField>
        );
      }
      return maybeConditional(props,
        <FormField
          key={node.id}
          name={fieldName}
          label={label}
          htmlFor={inputId}
          required={required}
          helpText={helpText}
          dataAttrs={dataAttrs}
        >
          {(nestedType === 'date' || nestedType === 'datetime' || nestedType === 'datetime-local') ? (
            <DatePicker
              id={inputId}
              name={fieldName}
              kind={nestedType === 'date' ? 'date' : 'datetime'}
              required={required}
              defaultValue={String(props.value ?? '')}
              placeholder={String(props.placeholder ?? '')}
              className={className}
              styleObj={styleObj}
              entityAttr={entityAttr}
              fieldAttr={fieldAttr}
            />
          ) : (
            <ValidatedInput
              id={inputId}
              className={`appbana-input ${className}`}
              style={styleObj}
              type={nestedType}
              placeholder={String(props.placeholder ?? '')}
              defaultValue={String(props.value ?? '')}
              name={fieldName}
              required={required}
              {...entityAttr}
              {...fieldAttr}
            />
          )}
        </FormField>
      );
    }

    case 'select': {
      const label = fieldLabel(props);
      const required = Boolean(props.required);
      const inputId = `appbana-sel-${node.id}`;
      const helpText = String(props.help ?? props.description ?? '');
      const fieldName = String(props.name ?? '');
      // If a select node references another entity, render a live-loaded ReferenceField.
      const refEntity = String(
        props.referenceEntity ?? props.entityName ?? ''
      );
      if (refEntity) {
        const refFieldName = String(props.name ?? refEntity);
        return maybeConditional(props,
          <FormField
            key={node.id}
            name={refFieldName}
            label={label}
            htmlFor={inputId}
            required={required}
            helpText={helpText}
            dataAttrs={dataAttrs}
          >
            <ReferenceField
              id={inputId}
              name={refFieldName}
              refEntity={refEntity}
              required={required}
              defaultValue={String(props.value ?? '')}
              className={className}
              styleObj={styleObj}
              entityAttr={entityAttr}
              fieldAttr={fieldAttr}
            />
          </FormField>
        );
      }
      return maybeConditional(props,
        <FormField
          key={node.id}
          name={fieldName}
          label={label}
          htmlFor={inputId}
          required={required}
          helpText={helpText}
          dataAttrs={dataAttrs}
        >
          <ValidatedSelect
            id={inputId}
            className={`appbana-select ${className}`}
            style={styleObj}
            name={fieldName}
            defaultValue={String(props.value ?? '')}
            required={required}
            {...entityAttr}
            {...fieldAttr}
          >
            {((props.options as string[]) ?? []).map((opt: string) => (
              <option key={opt} value={opt}>{opt}</option>
            ))}
          </ValidatedSelect>
        </FormField>
      );
    }

    case 'textarea': {
      const label = fieldLabel(props);
      const required = Boolean(props.required);
      const inputId = `appbana-ta-${node.id}`;
      const helpText = String(props.help ?? props.description ?? '');
      const fieldName = String(props.name ?? '');
      return maybeConditional(props,
        <FormField
          key={node.id}
          name={fieldName}
          label={label}
          htmlFor={inputId}
          required={required}
          helpText={helpText}
          full
          dataAttrs={dataAttrs}
        >
          <ValidatedTextarea
            id={inputId}
            className={`appbana-textarea ${className}`}
            style={styleObj}
            name={fieldName}
            placeholder={String(props.placeholder ?? '')}
            defaultValue={String(props.value ?? '')}
            rows={Number(props.rows ?? 4)}
            required={required}
            {...entityAttr}
            {...fieldAttr}
          />
        </FormField>
      );
    }

    case 'table':
    case 'grid':
    case 'appbana-table-live':
      return (
        <StudioTableLive
          key={node.id}
          node={node}
          pageId={pageId}
        />
      );

    case 'form':
    case 'studio-form':
      return (
        <EntityForm
          key={node.id}
          className={`appbana-form ${className}`}
          styleObj={styleObj}
          entity={String(props.entity ?? '')}
          dataAttrs={dataAttrs}
        >
          {children}
        </EntityForm>
      );

    case 'img':
      return (
        <img
          key={node.id}
          src={String(props.src ?? '')}
          alt={String(props.alt ?? '')}
          className={className}
          style={styleObj}
          {...dataAttrs}
        />
      );

    case 'a':
      return (
        <a
          key={node.id}
          href={String(props.href ?? '#')}
          className={className}
          style={styleObj}
          {...dataAttrs}
        >
          {String(props.text ?? 'Link')}
        </a>
      );

    case 'container':
    case 'section':
    case 'div':
    default:
      return (
        <div
          key={node.id}
          id={node.id}
          className={className}
          style={styleObj}
          {...dataAttrs}
        >
          {children}
        </div>
      );
  }
}

// ─── Reference-field renderer ────────────────────────────────────────────────

interface RefRow { id?: unknown; name?: unknown; title?: unknown; label?: unknown; [k: string]: unknown }

interface ReferenceFieldProps {
  id: string;
  name: string;
  refEntity: string;
  required: boolean;
  defaultValue: string;
  className: string;
  styleObj: React.CSSProperties;
  entityAttr: Record<string, string | undefined>;
  fieldAttr: Record<string, string | undefined>;
}

function optionLabelFor(row: RefRow): string {
  // Candidate field names (case-insensitive) in priority order.
  const preferred = [
    'name', 'full_name', 'fullname',
    'title',
    'label',
    'email', 'email_address',
    'code',
  ];
  const lookup: Record<string, unknown> = {};
  for (const key of Object.keys(row)) {
    lookup[key.toLowerCase()] = (row as Record<string, unknown>)[key];
  }
  for (const key of preferred) {
    const v = lookup[key];
    if (v != null && String(v).trim()) return String(v);
  }
  return `#${String(row.id ?? lookup['id'] ?? '')}`;
}

function ReferenceField(props: Readonly<ReferenceFieldProps>) {
  const { id, name, refEntity, required, defaultValue, className, styleObj, entityAttr, fieldAttr } = props;
  const [rows, setRows] = useState<RefRow[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    if (!refEntity) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError('');
    // Sprint 3 task 3.7 — probe with just enough rows to render the native
    // <select> AND detect the "too many options" case. The combobox path
    // does its own paginated fetches so we don't need to keep loading here.
    fetchEntityRows(qualifyEntityKey(refEntity), getRuntimeToken(), { limit: COMBOBOX_THRESHOLD + 1 })
      .then((result) => {
        if (cancelled) return;
        setRows(result.rows as RefRow[]);
        setTotal(result.total);
      })
      .catch((e) => { if (!cancelled) setError(e instanceof Error ? e.message : `Failed to load ${refEntity}`); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [refEntity]);

  if (loading) {
    return (
      <div className="appbana-reference-loading" aria-busy="true">
        <Skeleton
          className="h-9 w-full rounded-md"
          ariaLabel={`Loading ${refEntity || 'options'}`}
        />
      </div>
    );
  }
  if (error) {
    return (
      <div>
        <select id={id} className={`appbana-select ${className}`} disabled>
          <option>— unavailable —</option>
        </select>
        <p className="appbana-field-help" style={{ color: '#b91c1c' }}>{error}</p>
      </div>
    );
  }

  // Sprint 3 task 3.7 — swap to combobox when the target table has more
  // rows than a user can reasonably scan in a dropdown.
  if (total > COMBOBOX_THRESHOLD) {
    return (
      <ReferenceCombobox
        id={id}
        name={name}
        refEntity={refEntity}
        required={required}
        defaultValue={defaultValue}
        className={className}
        style={styleObj}
        entityAttr={entityAttr}
        fieldAttr={fieldAttr}
      />
    );
  }

  return (
    <ValidatedSelect
      id={id}
      name={name}
      className={`appbana-select ${className}`}
      style={styleObj}
      required={required}
      defaultValue={defaultValue}
      {...entityAttr}
      {...fieldAttr}
    >
      <option value="">— Select {refEntity} —</option>
      {rows.map((row) => {
        const rowRec = row as Record<string, unknown>;
        const val = String(row.id ?? rowRec.ID ?? rowRec.id ?? '');
        return <option key={val} value={val}>{optionLabelFor(row)}</option>;
      })}
    </ValidatedSelect>
  );
}

// ─── Entity form (Save wiring) ───────────────────────────────────────────────

interface EntityFormProps {
  className: string;
  styleObj: React.CSSProperties;
  entity: string;
  dataAttrs: Record<string, string>;
  children: React.ReactNode;
}

function EntityForm(props: Readonly<EntityFormProps>) {
  const { className, styleObj, entity, dataAttrs, children } = props;
  const [saving, setSaving] = useState(false);
  const { errors, validate, clearError, resetErrors, setExternalErrors } = useEntityFormValidation();
  // Phase B2 — publish live form values to descendant ConditionalField instances.
  const formElRef = useRef<HTMLFormElement | null>(null);

  async function submit(form: HTMLFormElement): Promise<boolean> {
    if (!entity) {
      toast.error('Save failed', { description: 'This form has no entity bound.' });
      return false;
    }
    const result = validate(form);
    if (!result.ok) {
      // Focus the first invalid input for keyboard users.
      const firstBad = Object.keys(result.errors)[0];
      if (firstBad) {
        const el = form.querySelector<HTMLElement>(`[name="${CSS.escape(firstBad)}"]`);
        el?.focus();
      }
      toast.error('Please fix the highlighted fields');
      return false;
    }
    const payload: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(result.data)) {
      payload[k] = v === '' || v === undefined ? null : v;
    }
    setSaving(true);
    try {
      const qualified = qualifyEntityKey(entity);
      await insertEntityRow(qualified, payload, getRuntimeToken());
      window.dispatchEvent(
        new CustomEvent('appbana:row-inserted', { detail: { entity: qualified } })
      );
      toast.success('Saved', { description: `New ${humanize(entity.split('_').pop())} added.` });
      return true;
    } catch (err) {
      // Sprint 3 task 3.1 — Backend 400s with a structured field-error
      // payload get merged into the form's error state so each bad input
      // gets a red outline + inline message, matching the client-side
      // Zod flow. Non-field errors (network, 5xx, generic) still surface
      // as a toast.
      if (err instanceof ApiFieldError && Object.keys(err.fieldErrors).length > 0) {
        setExternalErrors(err.fieldErrors);
        const firstBad = Object.keys(err.fieldErrors)[0];
        if (firstBad && firstBad !== '_form') {
          const el = form.querySelector<HTMLElement>(`[name="${CSS.escape(firstBad)}"]`);
          el?.focus();
        }
        toast.error('Please fix the highlighted fields', {
          description: err.fieldErrors._form ?? undefined,
        });
      } else {
        const msg = err instanceof Error ? err.message : 'Save failed';
        toast.error('Save failed', { description: msg });
      }
      return false;
    } finally {
      setSaving(false);
    }
  }

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const ok = await submit(form);
    if (ok) {
      form.reset();
      resetErrors();
    }
  }

  // "Save & Add another" — same as Save but keeps the user on the form so they
  // can quickly enter the next record. Reflected as a secondary button.
  async function handleSaveAndNew() {
    const form = document.querySelector<HTMLFormElement>(`form[data-entity="${entity}"]`);
    if (!form) return;
    const ok = await submit(form);
    if (ok) {
      form.reset();
      resetErrors();
      const first = form.querySelector<HTMLElement>('input, select, textarea');
      first?.focus();
    }
  }

  return (
    <EntityFormErrorProvider errors={errors} clearError={clearError}>
      <form
        ref={formElRef}
        className={className}
        style={styleObj}
        data-entity={entity}
        onSubmit={handleSubmit}
        noValidate
        {...dataAttrs}
      >
        <FormValuesProvider formRef={formElRef}>
          {children}
          <div className="appbana-form-save-cell">
            <FormActions
              saving={saving}
              onSaveAndNew={entity ? handleSaveAndNew : undefined}
            />
          </div>
        </FormValuesProvider>
      </form>
    </EntityFormErrorProvider>
  );
}

