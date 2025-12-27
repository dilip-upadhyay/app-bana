
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

    /**
     * Create a new template from an existing page
     */
    async createTemplate(templateData: Omit<PageTemplate, 'id' | 'isSystem'>): Promise<PageTemplate> {
        try {
            // Generate a URL-friendly ID from the name
            const timestamp = Date.now().toString(36);
            const slug = templateData.name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
            const id = `${slug}-${timestamp}`;

            const payload = {
                ...templateData,
                id,
                isSystem: false,
                category: templateData.category || 'user'
            };

            const response = await fetch('/api/templates', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (!response.ok) {
                throw new Error(`Failed to create template: ${response.statusText}`);
            }

            const newTemplate: PageTemplate = await response.json();
            this.templates.set(newTemplate.id, newTemplate);
            return newTemplate;
        } catch (error) {
            console.error('[TemplateStore] Error creating template:', error);
            throw error;
        }
    }
}

export const templateStore = TemplateStore.getInstance();
