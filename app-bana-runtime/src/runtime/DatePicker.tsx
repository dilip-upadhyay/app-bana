/**
 * DatePicker.tsx — Popover-based date / datetime picker for the runtime.
 *
 * Replaces native `<input type="date">` (which renders as `dd-mm-yyyy` on
 * many browsers and looks unprofessional in a client demo).
 *
 * Behaviour:
 *   - Input displays the human-formatted value (`MMM d, yyyy` for `date`,
 *     `MMM d, yyyy h:mm a` for `datetime`) using `date-fns`.
 *   - Clicking the input opens a `react-day-picker` calendar in a popover.
 *   - Selecting a day updates the display and the hidden `<input name="…">`
 *     with an ISO 8601 value so the backend contract is unchanged.
 *   - For `datetime` the popover also shows a `<input type="time">` field
 *     that combines with the selected date on submit.
 *   - Keyboard-navigable (Enter/Space open, Escape closes, Tab exits).
 *   - Emits the same `data-appbana-*` attrs Renderer.tsx would emit for the
 *     original native input, so Stage 6 select-and-instruct still works.
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { DayPicker } from 'react-day-picker';
import { format, isValid, parseISO } from 'date-fns';
import 'react-day-picker/dist/style.css';

export type DatePickerKind = 'date' | 'datetime';

export interface DatePickerProps {
  id: string;
  name: string;
  kind: DatePickerKind;
  required?: boolean;
  defaultValue?: string;
  placeholder?: string;
  className?: string;
  styleObj?: React.CSSProperties;
  /** Data attributes that Renderer.tsx would put on the underlying input. */
  entityAttr?: Record<string, string | undefined>;
  fieldAttr?: Record<string, string | undefined>;
}

const DISPLAY_FORMAT: Record<DatePickerKind, string> = {
  date: 'MMM d, yyyy',
  datetime: 'MMM d, yyyy h:mm a',
};

function parseInitial(raw: string | undefined): Date | undefined {
  if (!raw) return undefined;
  // Accept full ISO, date-only YYYY-MM-DD, and anything Date can parse.
  const iso = parseISO(raw);
  if (isValid(iso)) return iso;
  const d = new Date(raw);
  return isValid(d) ? d : undefined;
}

function toIso(kind: DatePickerKind, d: Date): string {
  if (kind === 'date') {
    // date-only stays date-only so the DB column doesn't get a spurious time.
    return format(d, 'yyyy-MM-dd');
  }
  return d.toISOString();
}

function toTimeInputValue(d: Date): string {
  return format(d, 'HH:mm');
}

export function DatePicker(props: DatePickerProps): JSX.Element {
  const {
    id,
    name,
    kind,
    required = false,
    defaultValue,
    placeholder,
    className = '',
    styleObj,
    entityAttr = {},
    fieldAttr = {},
  } = props;

  const initial = useMemo(() => parseInitial(defaultValue), [defaultValue]);
  const [selected, setSelected] = useState<Date | undefined>(initial);
  const [timeStr, setTimeStr] = useState<string>(
    initial && kind === 'datetime' ? toTimeInputValue(initial) : '09:00'
  );
  const [open, setOpen] = useState(false);
  const wrapRef = useRef<HTMLDivElement>(null);
  const displayRef = useRef<HTMLInputElement>(null);

  const displayValue = useMemo(() => {
    if (!selected) return '';
    let d = selected;
    if (kind === 'datetime' && timeStr) {
      const [hh, mm] = timeStr.split(':').map((s) => parseInt(s, 10));
      if (!Number.isNaN(hh) && !Number.isNaN(mm)) {
        d = new Date(d);
        d.setHours(hh, mm, 0, 0);
      }
    }
    return format(d, DISPLAY_FORMAT[kind]);
  }, [selected, timeStr, kind]);

  const hiddenValue = useMemo(() => {
    if (!selected) return '';
    let d = selected;
    if (kind === 'datetime' && timeStr) {
      const [hh, mm] = timeStr.split(':').map((s) => parseInt(s, 10));
      if (!Number.isNaN(hh) && !Number.isNaN(mm)) {
        d = new Date(d);
        d.setHours(hh, mm, 0, 0);
      }
    }
    return toIso(kind, d);
  }, [selected, timeStr, kind]);

  // Close popover on outside click.
  useEffect(() => {
    if (!open) return;
    function onDocClick(e: MouseEvent) {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', onDocClick);
    return () => document.removeEventListener('mousedown', onDocClick);
  }, [open]);

  // Close on Escape.
  const onKeyDown = useCallback((e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Escape') {
      setOpen(false);
      displayRef.current?.blur();
      return;
    }
    if ((e.key === 'Enter' || e.key === ' ') && !open) {
      e.preventDefault();
      setOpen(true);
    }
  }, [open]);

  const onDaySelect = useCallback((day: Date | undefined) => {
    setSelected(day);
    if (kind === 'date' && day) {
      // For plain dates, closing on selection is the expected UX.
      setOpen(false);
    }
  }, [kind]);

  return (
    <div ref={wrapRef} className="appbana-datepicker" style={{ position: 'relative' }}>
      <input
        ref={displayRef}
        id={id}
        className={`appbana-input ${className}`}
        style={styleObj}
        type="text"
        readOnly
        placeholder={placeholder ?? (kind === 'datetime' ? 'Pick date & time' : 'Pick a date')}
        value={displayValue}
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
        onKeyDown={onKeyDown}
        {...entityAttr}
        {...fieldAttr}
      />
      {/* Hidden input carries the ISO value that the form submits. Renderer's
          form-serialisation reads inputs by `name`, so the backend contract
          is preserved. */}
      <input
        type="hidden"
        name={name}
        value={hiddenValue}
        required={required}
        data-appbana-datepicker-value="true"
      />
      {open && (
        <div
          role="dialog"
          aria-label={kind === 'datetime' ? 'Select date and time' : 'Select date'}
          className="appbana-datepicker-popover"
        >
          <DayPicker
            mode="single"
            selected={selected}
            onSelect={onDaySelect}
            captionLayout="dropdown"
            startMonth={new Date(1970, 0)}
            endMonth={new Date(new Date().getFullYear() + 10, 11)}
            weekStartsOn={0}
          />
          {kind === 'datetime' && (
            <div className="appbana-datepicker-time">
              <label
                htmlFor={`${id}-time`}
                className="appbana-field-label"
                style={{ marginRight: '0.5rem' }}
              >
                Time
              </label>
              <input
                id={`${id}-time`}
                type="time"
                className="appbana-input"
                style={{ width: 'auto' }}
                value={timeStr}
                onChange={(e) => setTimeStr(e.currentTarget.value)}
              />
              <button
                type="button"
                className="appbana-datepicker-done"
                onClick={() => setOpen(false)}
              >
                Done
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
