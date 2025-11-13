// TableLive.ts - Runtime table renderer with live data, pagination, actions
import { ComponentNode } from '../../models/metadata';
import { fetchTableData } from '../../core/api-client';

export async function renderTableLive(node: ComponentNode, container: HTMLElement) {
  console.log('[TableLive] renderTableLive called', { node });
  const entity = node.props?.entity;
  const fields = Array.isArray(node.props?.fields) ? node.props.fields : [];
  const pageSize = node.props?.pageSize || 25;
  const sort = node.props?.sort || '';
  const actions = node.props?.actions || [];
  if (!entity || fields.length === 0) {
    container.innerHTML = '<div class="table-error">No entity or columns selected.</div>';
    return;
  }
  // Fetch data from backend
  console.log('[TableLive] fetchTableData', { entity, fields, pageSize, sort });
  const data = await fetchTableData(entity, fields.map(f => f.name), { pageSize, sort });
  // Render table
  container.innerHTML = '';
  const table = document.createElement('table');
  table.className = 'table-live';
  const thead = document.createElement('thead');
  const tr = document.createElement('tr');
  for (const field of fields) {
    const th = document.createElement('th');
    th.textContent = field.label || field.name;
    tr.appendChild(th);
  }
  if (actions.length > 0) {
    const th = document.createElement('th');
    th.textContent = 'Actions';
    tr.appendChild(th);
  }
  thead.appendChild(tr);
  table.appendChild(thead);
  const tbody = document.createElement('tbody');
  for (const row of data.rows || []) {
    const tr = document.createElement('tr');
    for (const field of fields) {
      const td = document.createElement('td');
      td.textContent = String(row[field.name] ?? '');
      tr.appendChild(td);
    }
    if (actions.length > 0) {
      const td = document.createElement('td');
      td.className = 'table-actions';
      for (const action of actions) {
        const btn = document.createElement('button');
        btn.textContent = action.charAt(0).toUpperCase() + action.slice(1);
        btn.onclick = () => alert(`${action} not implemented yet.`);
        td.appendChild(btn);
      }
      tr.appendChild(td);
    }
    tbody.appendChild(tr);
  }
  table.appendChild(tbody);
  container.appendChild(table);
  // TODO: Add pagination controls
}
