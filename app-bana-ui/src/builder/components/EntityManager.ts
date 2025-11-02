/**
 * EntityManager Component
 * Visual entity editor for business-friendly data modeling
 * Part of GAP #3: Entity Abstraction Layer
 */

import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { appStore } from '../store/AppStore';
import type { AppMeta } from '../../models/app-metadata';
import type { EntityMeta, EntityField, EntityRelationship } from '../../models/entity-metadata';
import { EntitySchemaConverter } from '../../core/EntitySchemaConverter';

@customElement('studio-entity-manager')
export class EntityManager extends LitElement {
  static styles = css`
    :host {
      display: flex;
      flex-direction: column;
      height: 100%;
      background: #f9fafb;
    }

    /* Header */
    .header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 1rem 1.5rem;
      background: white;
      border-bottom: 1px solid #e5e7eb;
      gap: 1rem;
    }

    .header-left {
      display: flex;
      align-items: center;
      gap: 1rem;
    }

    .header-title {
      font-size: 1.25rem;
      font-weight: 600;
      color: #111827;
      margin: 0;
    }

    .entity-count {
      padding: 0.25rem 0.75rem;
      background: #eff6ff;
      color: #1e40af;
      border-radius: 12px;
      font-size: 0.875rem;
      font-weight: 500;
    }

    /* Search & Actions */
    .search-box {
      flex: 1;
      max-width: 400px;
      position: relative;
    }

    .search-input {
      width: 100%;
      padding: 0.5rem 0.75rem 0.5rem 2.5rem;
      border: 1px solid #d1d5db;
      border-radius: 8px;
      font-size: 0.875rem;
      background: white;
    }

    .search-input:focus {
      outline: none;
      border-color: #3b82f6;
      box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
    }

    .search-icon {
      position: absolute;
      left: 0.75rem;
      top: 50%;
      transform: translateY(-50%);
      color: #9ca3af;
      pointer-events: none;
    }

    .btn {
      padding: 0.5rem 1rem;
      border: none;
      border-radius: 8px;
      font-size: 0.875rem;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.2s;
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .btn-primary {
      background: #3b82f6;
      color: white;
    }

    .btn-primary:hover {
      background: #2563eb;
    }

    .btn-secondary {
      background: white;
      color: #374151;
      border: 1px solid #d1d5db;
    }

    .btn-secondary:hover {
      background: #f9fafb;
    }

    /* Content */
    .content {
      flex: 1;
      overflow-y: auto;
      padding: 1.5rem;
    }

    /* Empty State */
    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 3rem;
      text-align: center;
      min-height: 400px;
    }

    .empty-icon {
      font-size: 4rem;
      margin-bottom: 1rem;
      opacity: 0.5;
    }

    .empty-title {
      font-size: 1.25rem;
      font-weight: 600;
      color: #111827;
      margin-bottom: 0.5rem;
    }

    .empty-description {
      color: #6b7280;
      margin-bottom: 1.5rem;
      max-width: 400px;
    }

    /* Entity Grid */
    .entity-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
      gap: 1rem;
    }

    /* Entity Card */
    .entity-card {
      background: white;
      border: 2px solid #e5e7eb;
      border-radius: 12px;
      padding: 1.25rem;
      cursor: pointer;
      transition: all 0.2s;
      position: relative;
    }

    .entity-card:hover {
      border-color: #3b82f6;
      box-shadow: 0 4px 12px rgba(59, 130, 246, 0.15);
      transform: translateY(-2px);
    }

    .entity-card.selected {
      border-color: #3b82f6;
      background: #eff6ff;
    }

    .entity-card-header {
      display: flex;
      align-items: flex-start;
      gap: 0.75rem;
      margin-bottom: 0.75rem;
    }

    .entity-icon {
      font-size: 2rem;
      width: 48px;
      height: 48px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #f3f4f6;
      border-radius: 8px;
      flex-shrink: 0;
    }

    .entity-info {
      flex: 1;
      min-width: 0;
    }

    .entity-name {
      font-size: 1rem;
      font-weight: 600;
      color: #111827;
      margin-bottom: 0.25rem;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .entity-description {
      font-size: 0.875rem;
      color: #6b7280;
      line-height: 1.4;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }

    .entity-meta {
      display: flex;
      gap: 1rem;
      margin-top: 0.75rem;
      padding-top: 0.75rem;
      border-top: 1px solid #e5e7eb;
    }

    .entity-meta-item {
      display: flex;
      align-items: center;
      gap: 0.375rem;
      font-size: 0.75rem;
      color: #6b7280;
    }

    .entity-meta-item svg {
      width: 14px;
      height: 14px;
    }

    .entity-actions {
      position: absolute;
      top: 1rem;
      right: 1rem;
      display: flex;
      gap: 0.5rem;
      opacity: 0;
      transition: opacity 0.2s;
    }

    .entity-card:hover .entity-actions {
      opacity: 1;
    }

    .icon-btn {
      width: 28px;
      height: 28px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: white;
      border: 1px solid #d1d5db;
      border-radius: 6px;
      cursor: pointer;
      transition: all 0.2s;
    }

    .icon-btn:hover {
      background: #f3f4f6;
      border-color: #9ca3af;
    }

    .icon-btn svg {
      width: 14px;
      height: 14px;
      color: #6b7280;
    }

    /* Modal */
    .modal-overlay {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(0, 0, 0, 0.5);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
      padding: 2rem;
    }

    .modal {
      background: white;
      border-radius: 12px;
      max-width: 600px;
      width: 100%;
      max-height: 90vh;
      overflow-y: auto;
      box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1);
    }

    .modal-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 1.5rem;
      border-bottom: 1px solid #e5e7eb;
    }

    .modal-title {
      font-size: 1.25rem;
      font-weight: 600;
      color: #111827;
      margin: 0;
    }

    .modal-body {
      padding: 1.5rem;
    }

    .modal-footer {
      display: flex;
      justify-content: flex-end;
      gap: 0.75rem;
      padding: 1.5rem;
      border-top: 1px solid #e5e7eb;
    }

    /* Form */
    .form-group {
      margin-bottom: 1.25rem;
    }

    .form-label {
      display: block;
      font-size: 0.875rem;
      font-weight: 500;
      color: #374151;
      margin-bottom: 0.5rem;
    }

    .form-input,
    .form-textarea,
    .form-select {
      width: 100%;
      padding: 0.5rem 0.75rem;
      border: 1px solid #d1d5db;
      border-radius: 8px;
      font-size: 0.875rem;
      font-family: inherit;
    }

    .form-input:focus,
    .form-textarea:focus,
    .form-select:focus {
      outline: none;
      border-color: #3b82f6;
      box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
    }

    .form-textarea {
      resize: vertical;
      min-height: 80px;
    }

    .form-hint {
      font-size: 0.75rem;
      color: #6b7280;
      margin-top: 0.25rem;
    }

    .form-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1rem;
    }

    /* Loader */
    .loader {
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 3rem;
    }

    .spinner {
      width: 40px;
      height: 40px;
      border: 3px solid #e5e7eb;
      border-top-color: #3b82f6;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    /* Toast */
    .toast {
      position: fixed;
      bottom: 2rem;
      right: 2rem;
      background: white;
      padding: 1rem 1.5rem;
      border-radius: 8px;
      box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1);
      display: flex;
      align-items: center;
      gap: 0.75rem;
      z-index: 1001;
      animation: slideIn 0.3s ease-out;
    }

    @keyframes slideIn {
      from {
        transform: translateX(400px);
        opacity: 0;
      }
      to {
        transform: translateX(0);
        opacity: 1;
      }
    }

    .toast.success {
      border-left: 4px solid #10b981;
    }

    .toast.error {
      border-left: 4px solid #ef4444;
    }

    /* SQL Preview */
    .sql-preview-section {
      margin-top: 1.5rem;
      border: 1px solid #e5e7eb;
      border-radius: 6px;
      overflow: hidden;
    }

    .sql-preview-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0.75rem 1rem;
      background: #f9fafb;
      cursor: pointer;
      user-select: none;
      transition: background 0.2s;
    }

    .sql-preview-header:hover {
      background: #f3f4f6;
    }

    .sql-preview-title {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-weight: 500;
      color: #374151;
    }

    .sql-preview-toggle {
      color: #6b7280;
      font-size: 0.875rem;
    }

    .sql-preview-body {
      padding: 1rem;
      background: #1f2937;
      max-height: 300px;
      overflow-y: auto;
    }

    .sql-preview-code {
      margin: 0;
      font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', monospace;
      font-size: 0.875rem;
      line-height: 1.6;
      color: #e5e7eb;
      white-space: pre;
      overflow-x: auto;
    }

    .preview-placeholder {
      padding: 2rem;
      text-align: center;
      color: #9ca3af;
      font-style: italic;
    }

    .preview-error {
      padding: 1rem;
      background: #fef2f2;
      color: #dc2626;
      border-radius: 4px;
      font-size: 0.875rem;
    }
  `;

  @state() private currentApp: AppMeta | undefined;
  @state() private entities: EntityMeta[] = [];
  @state() private selectedEntityId: string | null = null;
  @state() private searchQuery = '';
  @state() private showCreateModal = false;
  @state() private loading = false;
  @state() private toast: { message: string; type: 'success' | 'error' } | null = null;

  // Form state for creating new entity
  @state() private formData: Partial<EntityMeta> = this.getEmptyEntityForm();
  @state() private showSQLPreview = false;

  connectedCallback() {
    super.connectedCallback();
    this.loadEntities();
    
    // Subscribe to app changes
    appStore.subscribe(() => {
      this.currentApp = appStore.getCurrentApp();
      this.loadEntities();
    });
  }

  private getEmptyEntityForm(): Partial<EntityMeta> {
    return {
      name: '',
      displayName: '',
      description: '',
      icon: '📦',
      datasource: 'default',
      fields: [],
      relationships: [],
    };
  }

  private loadEntities() {
    if (!this.currentApp) {
      this.entities = [];
      return;
    }

    // Load entities from current app
    this.entities = this.currentApp.entities || [];
  }

  private get filteredEntities(): EntityMeta[] {
    if (!this.searchQuery) {
      return this.entities;
    }

    const query = this.searchQuery.toLowerCase();
    return this.entities.filter(entity =>
      entity.displayName.toLowerCase().includes(query) ||
      entity.name.toLowerCase().includes(query) ||
      entity.description?.toLowerCase().includes(query)
    );
  }

  private handleSearchInput(e: Event) {
    const input = e.target as HTMLInputElement;
    this.searchQuery = input.value;
  }

  private handleCreateEntity() {
    this.formData = this.getEmptyEntityForm();
    this.showCreateModal = true;
  }

  private handleCloseModal() {
    this.showCreateModal = false;
    this.formData = this.getEmptyEntityForm();
  }

  private handleFormInput(e: Event) {
    const target = e.target as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement;
    const { name, value } = target;
    this.formData = { ...this.formData, [name]: value };
  }

  private toggleSQLPreview() {
    this.showSQLPreview = !this.showSQLPreview;
  }

  private renderSQLPreview() {
    if (!this.formData.name) {
      return html`
        <div class="preview-placeholder">
          Enter entity details above to see the SQL preview
        </div>
      `;
    }

    try {
      const entityId = this.formData.name.toLowerCase().replaceAll(/\s+/g, '-');
      
      const tempEntity: EntityMeta = {
        id: entityId,
        name: this.formData.name || entityId,
        displayName: this.formData.displayName || entityId,
        description: this.formData.description || '',
        icon: this.formData.icon || '📦',
        datasource: this.formData.datasource || 'default',
        fields: [],
        relationships: [],
        rules: [],
        permissions: {
          roles: {
            admin: { create: true, read: true, update: true, delete: true },
            user: { create: false, read: true, update: false, delete: false }
          },
          auditLog: true
        },
        softDelete: true,
        versioning: true
      };

      const sql = EntitySchemaConverter.generateDDL(tempEntity);
      
      return html`
        <pre class="sql-preview-code">${sql}</pre>
      `;
    } catch (error) {
      return html`
        <div class="preview-error">
          Error generating SQL: ${error instanceof Error ? error.message : 'Unknown error'}
        </div>
      `;
    }
  }

  private async handleSaveEntity() {
    if (!this.currentApp) {
      this.showToast('No app selected', 'error');
      return;
    }

    // Validate form
    if (!this.formData.name || !this.formData.displayName) {
      this.showToast('Name and display name are required', 'error');
      return;
    }

    this.loading = true;

    try {
      // Create entity ID from name (lowercase, no spaces)
      const entityId = (this.formData.name as string).toLowerCase().replace(/\s+/g, '-');

      // Check if entity already exists
      if (this.entities.some(e => e.id === entityId)) {
        this.showToast('Entity with this name already exists', 'error');
        this.loading = false;
        return;
      }

      // Create entity metadata
      const newEntity: EntityMeta = {
        id: entityId,
        name: this.formData.name || entityId,
        displayName: this.formData.displayName || entityId,
        description: this.formData.description || '',
        icon: this.formData.icon || '📦',
        datasource: this.formData.datasource || 'default',
        fields: [],
        relationships: [],
        created: Date.now(),
        updated: Date.now(),
      };

      // Add entity to app
      const updatedEntities = [...this.entities, newEntity];
      appStore.updateApp(this.currentApp.id, {
        entities: updatedEntities,
      });

      // Reload entities
      this.loadEntities();

      // Show success
      this.showToast(`Entity "${newEntity.displayName}" created!`, 'success');

      // Close modal
      this.handleCloseModal();
    } catch (error) {
      console.error('Failed to create entity:', error);
      this.showToast('Failed to create entity', 'error');
    } finally {
      this.loading = false;
    }
  }

  private handleSelectEntity(entityId: string) {
    this.selectedEntityId = this.selectedEntityId === entityId ? null : entityId;
  }

  private async handleDeleteEntity(entityId: string) {
    if (!this.currentApp) return;

    const entity = this.entities.find(e => e.id === entityId);
    if (!entity) return;

    if (!confirm(`Delete entity "${entity.displayName}"?\n\nThis cannot be undone.`)) {
      return;
    }

    try {
      const updatedEntities = this.entities.filter(e => e.id !== entityId);
      appStore.updateApp(this.currentApp.id, {
        entities: updatedEntities,
      });

      this.loadEntities();
      this.showToast(`Entity "${entity.displayName}" deleted`, 'success');

      if (this.selectedEntityId === entityId) {
        this.selectedEntityId = null;
      }
    } catch (error) {
      console.error('Failed to delete entity:', error);
      this.showToast('Failed to delete entity', 'error');
    }
  }

  private showToast(message: string, type: 'success' | 'error') {
    this.toast = { message, type };
    setTimeout(() => {
      this.toast = null;
    }, 3000);
  }

  render() {
    if (!this.currentApp) {
      return this.renderNoAppState();
    }

    return html`
      <div class="header">
        <div class="header-left">
          <h1 class="header-title">Entities</h1>
          ${this.entities.length > 0 ? html`
            <span class="entity-count">${this.entities.length}</span>
          ` : ''}
        </div>
        
        ${this.entities.length > 0 ? html`
          <div class="search-box">
            <span class="search-icon">🔍</span>
            <input
              type="text"
              class="search-input"
              placeholder="Search entities..."
              .value=${this.searchQuery}
              @input=${this.handleSearchInput}
            />
          </div>
        ` : ''}

        <button class="btn btn-primary" @click=${this.handleCreateEntity}>
          <span>+</span>
          <span>New Entity</span>
        </button>
      </div>

      <div class="content">
        ${this.entities.length === 0 ? this.renderEmptyState() : this.renderEntityGrid()}
      </div>

      ${this.showCreateModal ? this.renderCreateModal() : ''}
      ${this.toast ? this.renderToast() : ''}
    `;
  }

  private renderNoAppState() {
    return html`
      <div class="empty-state">
        <div class="empty-icon">📱</div>
        <h2 class="empty-title">No App Selected</h2>
        <p class="empty-description">
          Please select or create an app to manage entities.
        </p>
      </div>
    `;
  }

  private renderEmptyState() {
    return html`
      <div class="empty-state">
        <div class="empty-icon">📦</div>
        <h2 class="empty-title">No Entities Yet</h2>
        <p class="empty-description">
          Entities are business objects like Customer, Order, or Product.
          Create your first entity to get started!
        </p>
        <button class="btn btn-primary" @click=${this.handleCreateEntity}>
          <span>+</span>
          <span>Create First Entity</span>
        </button>
      </div>
    `;
  }

  private renderEntityGrid() {
    const entities = this.filteredEntities;

    if (entities.length === 0) {
      return html`
        <div class="empty-state">
          <div class="empty-icon">🔍</div>
          <h2 class="empty-title">No Results</h2>
          <p class="empty-description">
            No entities match your search "${this.searchQuery}"
          </p>
        </div>
      `;
    }

    return html`
      <div class="entity-grid">
        ${entities.map(entity => this.renderEntityCard(entity))}
      </div>
    `;
  }

  private renderEntityCard(entity: EntityMeta) {
    const isSelected = this.selectedEntityId === entity.id;
    const fieldCount = entity.fields.length;
    const relationshipCount = entity.relationships?.length || 0;

    return html`
      <div
        class="entity-card ${isSelected ? 'selected' : ''}"
        @click=${() => this.handleSelectEntity(entity.id)}
      >
        <div class="entity-card-header">
          <div class="entity-icon">${entity.icon || '📦'}</div>
          <div class="entity-info">
            <div class="entity-name">${entity.displayName}</div>
            ${entity.description ? html`
              <div class="entity-description">${entity.description}</div>
            ` : ''}
          </div>
        </div>

        <div class="entity-meta">
          <div class="entity-meta-item">
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
            <span>${fieldCount} field${fieldCount === 1 ? '' : 's'}</span>
          </div>
          ${relationshipCount > 0 ? html`
            <div class="entity-meta-item">
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1" />
              </svg>
              <span>${relationshipCount}</span>
            </div>
          ` : ''}
        </div>

        <div class="entity-actions" @click=${(e: Event) => e.stopPropagation()}>
          <button
            class="icon-btn"
            @click=${() => this.handleDeleteEntity(entity.id)}
            title="Delete entity"
          >
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
            </svg>
          </button>
        </div>
      </div>
    `;
  }

  private renderCreateModal() {
    return html`
      <div class="modal-overlay" @click=${this.handleCloseModal}>
        <div class="modal" @click=${(e: Event) => e.stopPropagation()}>
          <div class="modal-header">
            <h2 class="modal-title">Create New Entity</h2>
            <button class="icon-btn" @click=${this.handleCloseModal}>
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          <div class="modal-body">
            <div class="form-group">
              <label class="form-label">Entity Name *</label>
              <input
                type="text"
                name="name"
                class="form-input"
                placeholder="e.g., customer, order, product"
                .value=${this.formData.name || ''}
                @input=${this.handleFormInput}
              />
              <div class="form-hint">Technical name (lowercase, no spaces)</div>
            </div>

            <div class="form-group">
              <label class="form-label">Display Name *</label>
              <input
                type="text"
                name="displayName"
                class="form-input"
                placeholder="e.g., Customer, Order, Product"
                .value=${this.formData.displayName || ''}
                @input=${this.handleFormInput}
              />
              <div class="form-hint">User-friendly name</div>
            </div>

            <div class="form-group">
              <label class="form-label">Description</label>
              <textarea
                name="description"
                class="form-textarea"
                placeholder="Describe what this entity represents..."
                .value=${this.formData.description || ''}
                @input=${this.handleFormInput}
              ></textarea>
            </div>

            <div class="form-row">
              <div class="form-group">
                <label class="form-label">Icon</label>
                <input
                  type="text"
                  name="icon"
                  class="form-input"
                  placeholder="📦"
                  .value=${this.formData.icon || '📦'}
                  @input=${this.handleFormInput}
                />
                <div class="form-hint">Emoji or icon name</div>
              </div>

              <div class="form-group">
                <label class="form-label">Datasource</label>
                <select
                  name="datasource"
                  class="form-select"
                  .value=${this.formData.datasource || 'default'}
                  @change=${this.handleFormInput}
                >
                  <option value="default">Default</option>
                </select>
              </div>
            </div>

            <!-- SQL Preview Section -->
            <div class="sql-preview-section">
              <div class="sql-preview-header" @click=${this.toggleSQLPreview}>
                <span class="sql-preview-title">
                  <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" width="16" height="16">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4m0 5c0 2.21-3.582 4-8 4s-8-1.79-8-4" />
                  </svg>
                  SQL Preview
                </span>
                <span class="sql-preview-toggle">${this.showSQLPreview ? '▼' : '▶'}</span>
              </div>
              ${this.showSQLPreview ? html`
                <div class="sql-preview-body">
                  ${this.renderSQLPreview()}
                </div>
              ` : ''}
            </div>
          </div>

          <div class="modal-footer">
            <button class="btn btn-secondary" @click=${this.handleCloseModal}>
              Cancel
            </button>
            <button
              class="btn btn-primary"
              @click=${this.handleSaveEntity}
              ?disabled=${this.loading}
            >
              ${this.loading ? 'Creating...' : 'Create Entity'}
            </button>
          </div>
        </div>
      </div>
    `;
  }

  private renderToast() {
    if (!this.toast) return '';

    return html`
      <div class="toast ${this.toast.type}">
        <span>${this.toast.type === 'success' ? '✓' : '✗'}</span>
        <span>${this.toast.message}</span>
      </div>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'studio-entity-manager': EntityManager;
  }
}
