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
    appType?: string;
}

export interface ChatResponse {
    message: string;
    intent?: string;
    suggestions?: string[];
    conversationId?: string;
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
        const response = await fetch(`${this.baseUrl}/chat`, {
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
