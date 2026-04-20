export interface ChatMessage {
    role: 'user' | 'assistant';
    content: string;
    timestamp: Date;
    conversationId?: string;
}

export interface ChatRequest {
    message: string;
    sessionId: string;
    userId: string;
    token?: string;
    appType?: string;
    tenantId?: string;
    appId?: string;
    provider?: string;
    images?: string[];
}

export interface ChatResponse {
    message: string;
    intent?: string;
    suggestions?: string[];
    conversationId?: string;
}

export interface ChatSession {
    sessionId: string;
    lastActivity: number;
}

export interface AppPattern {
    id: string;
    patternName: string;
    appType: string;
    entities: any[];
    pages: any[];
    usageCount: number;
    successRate: number;
}

export class AiChatService {
    // AI Builder runs as separate microservice on port 8081
    private baseUrl = 'http://localhost:8081/api/ai';

    async sendMessage(request: ChatRequest): Promise<ChatResponse> {
        const response = await fetch(`${this.baseUrl}/chat/agent`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(request)
        });

        if (!response.ok) {
            throw new Error(`Chat request failed: ${response.statusText}`);
        }

        return response.json();
    }

    async getPatterns(appType?: string, limit: number = 10): Promise<AppPattern[]> {
        const params = new URLSearchParams();
        if (appType) params.set('appType', appType);
        params.set('limit', limit.toString());

        const response = await fetch(`${this.baseUrl}/patterns?${params}`);

        if (!response.ok) {
            throw new Error(`Failed to fetch patterns: ${response.statusText}`);
        }

        return response.json();
    }

    /**
     * Load persisted chat history for a user's session from the backend.
     * Returns messages in chronological order (oldest first).
     */
    async getHistory(userId: string, sessionId: string): Promise<ChatMessage[]> {
        const params = new URLSearchParams({ userId, sessionId });
        const response = await fetch(`${this.baseUrl}/chat/history?${params}`);

        if (!response.ok) {
            // Non-fatal: if history load fails, start fresh
            console.warn('[AiChatService] Could not load history:', response.statusText);
            return [];
        }

        const data = await response.json();
        const rawMessages: Array<{ role: string; content: string; timestamp?: number }> =
            data.messages || [];

        return rawMessages.map(m => ({
            role: m.role as 'user' | 'assistant',
            content: m.content,
            timestamp: m.timestamp ? new Date(m.timestamp) : new Date()
        }));
    }

    /**
     * Load recent past sessions for the user.
     */
    async getSessions(userId: string, limit: number = 20): Promise<ChatSession[]> {
        const params = new URLSearchParams({ userId, limit: limit.toString() });
        const response = await fetch(`${this.baseUrl}/chat/sessions?${params}`);

        if (!response.ok) {
            console.warn('[AiChatService] Could not load sessions:', response.statusText);
            return [];
        }

        const data = await response.json();
        return data.sessions || [];
    }

    async submitFeedback(conversationId: string, userId: string, rating: number, comment?: string): Promise<void> {
        const response = await fetch(`${this.baseUrl}/feedback`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                conversationId,
                userId,
                rating,
                feedbackType: rating > 0 ? 'thumbs_up' : 'thumbs_down',
                comment: comment || ''
            })
        });

        if (!response.ok) {
            throw new Error(`Failed to submit feedback: ${response.statusText}`);
        }
    }
}
