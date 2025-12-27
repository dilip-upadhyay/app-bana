
import { ComponentNode } from '../components/ComponentNode';

export interface PageTemplate {
    id: string;
    name: string;
    description: string;
    category: string;
    isSystem: boolean;
    nodes: ComponentNode[];
}

export class TemplateStore {
    private static instance: TemplateStore;
    private templates: Map<string, PageTemplate> = new Map();
    private loaded = false;

    private constructor() { }

    public static getInstance(): TemplateStore {
        if (!TemplateStore.instance) {
            TemplateStore.instance = new TemplateStore();
        }
        return TemplateStore.instance;
    }

    /**
     * Load all templates from the backend API
     */
    async loadTemplates(): Promise<PageTemplate[]> {
        if (this.loaded && this.templates.size > 0) {
            return Array.from(this.templates.values());
        }

        try {
            console.log('[TemplateStore] Fetching templates from /api/templates...');
            const response = await fetch('/api/templates');

            if (!response.ok) {
                throw new Error(`Failed to fetch templates: ${response.status} ${response.statusText}`);
            }

            const templatesList: PageTemplate[] = await response.json();

            this.templates.clear();
            templatesList.forEach(template => {
                this.templates.set(template.id, template);
            });

            this.loaded = true;
            console.log(`[TemplateStore] Successfully loaded ${this.templates.size} templates`);
            return templatesList;
        } catch (error) {
            console.error('[TemplateStore] Error loading templates:', error);
            // Fallback or rethrow depending on needs. For now rethrow so UI can handle
            throw error;
        }
    }

    /**
     * Get a specific template by ID (synchronous access to cached templates)
     */
    getTemplate(id: string): PageTemplate | undefined {
        return this.templates.get(id);
    }

    /**
     * Get all loaded templates
     */
    getAllTemplates(): PageTemplate[] {
        return Array.from(this.templates.values());
    }

    /**
     * Force reload templates from server
     */
    async reloadTemplates(): Promise<PageTemplate[]> {
        this.loaded = false;
        return this.loadTemplates();
    }
}

export const templateStore = TemplateStore.getInstance();
