import { BaseElement } from '../core/BaseElement';
import { getFieldPermissions, canReadField, canEditField } from '../core/api-client';

/**
 * FormElement - Base class for all form input components with Field-Level Security (FLS)
 * 
 * Provides FLS capabilities:
 * - loadFieldPermissions(entityName): Load permissions for entity
 * - canReadField(fieldName): Check if field is readable
 * - canEditField(fieldName): Check if field is editable
 * - isFieldHidden(fieldName): Convenience method for visibility
 * - isFieldDisabled(fieldName): Convenience method for disabled state
 * 
 * Usage:
 * ```typescript
 * export class InputElement extends FormElement {
 *   async connectedCallback() {
 *     super.connectedCallback();
 *     await this.loadFieldPermissionsFromAttribute();
 *   }
 *   
 *   protected render(): string {
 *     const fieldName = this.getAttribute('name') || '';
 *     
 *     // Hide non-readable fields
 *     if (this.isFieldHidden(fieldName)) {
 *       return this.renderHiddenField();
 *     }
 *     
 *     // Disable non-editable fields
 *     const disabled = this.isFieldDisabled(fieldName);
 *     const lockIcon = disabled ? ' 🔒' : '';
 *     
 *     return `<input ${disabled ? 'disabled' : ''} ... />`;
 *   }
 * }
 * ```
 */
export abstract class FormElement extends BaseElement {
  protected fieldPermissions?: { readable: string[]; editable: string[] };
  protected entityName?: string;
  private permissionLoadPromise?: Promise<void>;

  /**
   * Load field permissions for a specific entity.
   * Results are cached for 5 minutes by the API client.
   * 
   * @param entityName - Entity name (e.g., "user", "employee", "order")
   */
  async loadFieldPermissions(entityName: string): Promise<void> {
    // Avoid multiple simultaneous loads
    if (this.permissionLoadPromise) {
      return this.permissionLoadPromise;
    }

    this.permissionLoadPromise = (async () => {
      try {
        this.entityName = entityName;
        this.fieldPermissions = await getFieldPermissions(entityName);
        this.requestRender(); // Re-render with permissions
      } catch (error) {
        console.warn(
          `[FormElement] Failed to load field permissions for entity "${entityName}":`,
          error
        );
        // Default to full access on error (graceful degradation)
        this.fieldPermissions = { readable: ['*'], editable: ['*'] };
      } finally {
        this.permissionLoadPromise = undefined;
      }
    })();

    return this.permissionLoadPromise;
  }

  /**
   * Load field permissions from 'entity' or 'data-entity' attribute.
   * Call this in connectedCallback() if your component uses entity attribute.
   */
  protected async loadFieldPermissionsFromAttribute(): Promise<void> {
    const entityName =
      this.getAttribute('entity') || this.getAttribute('data-entity');
    
    if (entityName) {
      await this.loadFieldPermissions(entityName);
    }
  }

  /**
   * Check if field is readable by current user.
   * Returns true if no permissions loaded (default allow).
   * 
   * @param fieldName - Field name to check
   * @returns true if user can read field, false otherwise
   */
  protected canReadFieldInternal(fieldName: string): boolean {
    if (!fieldName) return true;
    if (!this.fieldPermissions) return true; // No permissions loaded = allow

    return canReadField(fieldName, this.fieldPermissions.readable);
  }

  /**
   * Check if field is editable by current user.
   * Returns false if field is not readable (cannot edit what you cannot see).
   * 
   * @param fieldName - Field name to check
   * @returns true if user can edit field, false otherwise
   */
  protected canEditFieldInternal(fieldName: string): boolean {
    if (!fieldName) return true;
    if (!this.fieldPermissions) return true; // No permissions loaded = allow

    // Cannot edit what you cannot read
    if (!this.canReadFieldInternal(fieldName)) {
      return false;
    }

    return canEditField(fieldName, this.fieldPermissions.editable);
  }

  /**
   * Check if field should be hidden (not readable).
   * Convenience method for render logic.
   * 
   * @param fieldName - Field name to check
   * @returns true if field should be hidden, false otherwise
   */
  protected isFieldHidden(fieldName: string): boolean {
    return !this.canReadFieldInternal(fieldName);
  }

  /**
   * Check if field should be disabled (readable but not editable).
   * Convenience method for render logic.
   * 
   * @param fieldName - Field name to check
   * @returns true if field should be disabled, false otherwise
   */
  protected isFieldDisabled(fieldName: string): boolean {
    // Don't disable if hidden
    if (this.isFieldHidden(fieldName)) {
      return false;
    }

    return !this.canEditFieldInternal(fieldName);
  }

  /**
   * Render a hidden field placeholder.
   * Override this to customize the hidden field message.
   * 
   * @returns HTML string for hidden field
   */
  protected renderHiddenField(): string {
    return `
      <div part="hidden-field" class="field-hidden" style="display: none;" title="Field hidden by administrator">
        <!-- Field hidden due to insufficient permissions -->
      </div>
    `;
  }

  /**
   * Get lock icon HTML for read-only fields.
   * 
   * @returns Lock emoji with spacing
   */
  protected getLockIcon(): string {
    return ' 🔒';
  }

  /**
   * Get tooltip text for disabled/read-only fields.
   * 
   * @returns Tooltip text
   */
  protected getDisabledTooltip(): string {
    return 'Field is read-only (no edit permission)';
  }

  /**
   * Get tooltip text for hidden fields.
   * 
   * @returns Tooltip text
   */
  protected getHiddenTooltip(): string {
    return 'Field hidden by administrator';
  }

  /**
   * Clear cached permissions (e.g., on role change).
   * Call this if user's roles change during session.
   */
  clearPermissions(): void {
    this.fieldPermissions = undefined;
    this.entityName = undefined;
    this.permissionLoadPromise = undefined;
  }
}
