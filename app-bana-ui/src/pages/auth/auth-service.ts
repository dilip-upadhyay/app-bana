export interface User {
    id: number;
    email: string;
    name: string;
    tenantId: string;
    status: string;
}

export interface AuthResponse {
    user: User;
    token: string;
    message: string;
}

const API_BASE = 'http://localhost:8080/api/auth';

export const AuthService = {
    async register(name: string, email: string, password: string): Promise<AuthResponse> {
        const response = await fetch(`${API_BASE}/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, email, password })
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || 'Registration failed');
        }

        const data = await response.json();
        this.setSession(data);
        return data;
    },

    async login(email: string, password: string): Promise<AuthResponse> {
        const response = await fetch(`${API_BASE}/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password })
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || 'Login failed');
        }

        const data = await response.json();
        this.setSession(data);
        return data;
    },

    logout() {
        localStorage.removeItem('appbana_token');
        localStorage.removeItem('appbana_user');
        window.location.href = '/login';
    },

    setSession(data: AuthResponse) {
        localStorage.setItem('appbana_token', data.token);
        localStorage.setItem('appbana_user', JSON.stringify(data.user));
    },

    getToken(): string | null {
        return localStorage.getItem('appbana_token');
    },

    getUser(): User | null {
        const u = localStorage.getItem('appbana_user');
        return u ? JSON.parse(u) : null;
    },

    isAuthenticated(): boolean {
        return !!this.getToken();
    }
};
