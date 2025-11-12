import { ComponentNode } from '../../models/metadata';

/**
 * TablePreview - Renders a minimal table showing selected columns in preview mode
 * Props:
 *   - fields: Array<{ name: string, label?: string, type?: string }>
 */
export function renderTablePreview(node: ComponentNode): HTMLElement {
  const fields = Array.isArray(node.props?.fields) ? node.props.fields : [];
  const table = document.createElement('table');
  table.className = 'table-minimal';
  const thead = document.createElement('thead');
  const tr = document.createElement('tr');
  if (fields.length === 0) {
    const th = document.createElement('th');
    th.textContent = 'No columns selected';
    th.colSpan = 1;
    tr.appendChild(th);
  } else {
    for (const field of fields) {
      const th = document.createElement('th');
      th.textContent = field.label || field.name;
      tr.appendChild(th);
    }
  }
  thead.appendChild(tr);
  table.appendChild(thead);
  // No data rows in preview, just show columns
  return table;
}
