/**
 * TableHeader.tsx — Sprint 3 task 3.12.
 *
 * Header row for entity tables. Kept dumb: takes an ordered list of
 * column names + a lookup for human labels and emits `<th>` cells plus
 * the row-actions column header that StudioTableLive relies on.
 */
import { humanizeHeader } from './cell-formatters';

export interface TableColumnMeta {
  readonly name: string;
  readonly label?: string;
}

export interface TableHeaderProps {
  readonly columns: readonly string[];
  readonly labelFor: (name: string) => string | undefined;
}

export function TableHeader({ columns, labelFor }: Readonly<TableHeaderProps>) {
  return (
    <thead>
      <tr>
        {columns.map((name) => (
          <th key={name} className="appbana-table-th" scope="col">
            {humanizeHeader(labelFor(name) ?? name)}
          </th>
        ))}
        {/* Row-actions column header — visually blank but accessible. */}
        <th className="appbana-table-th w-10" scope="col">
          <span className="sr-only">Actions</span>
        </th>
      </tr>
    </thead>
  );
}
