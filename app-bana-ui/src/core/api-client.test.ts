/**
 * Basic Tests for API Client
 * Run with: npm test api-client.test.ts
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ApiClient } from './api-client';
import { InterceptorManager } from './api-interceptor';
import type { Interceptor, ApiError } from './api-interceptor';

// Mock fetch globally
global.fetch = vi.fn();

describe('ApiClient', () => {
  let client: ApiClient;

  beforeEach(() => {
    client = new ApiClient({ baseUrl: 'http://localhost:8080' });
    vi.clearAllMocks();
  });

  it('should make GET request', async () => {
    const mockData = { id: 1, name: 'Test' };
    (global.fetch as any).mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Map([['content-type', 'application/json']]),
      json: async () => mockData,
    });

    const result = await client.get('/api/users');

    expect(global.fetch).toHaveBeenCalledWith(
      'http://localhost:8080/api/users',
      expect.objectContaining({
        method: 'GET',
      })
    );
    expect(result).toEqual(mockData);
  });

  it('should make POST request with body', async () => {
    const requestData = { name: 'John Doe' };
    const responseData = { id: 1, ...requestData };

    (global.fetch as any).mockResolvedValueOnce({
      ok: true,
      status: 201,
      headers: new Map([['content-type', 'application/json']]),
      json: async () => responseData,
    });

    const result = await client.post('/api/users', requestData);

    expect(global.fetch).toHaveBeenCalledWith(
      'http://localhost:8080/api/users',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify(requestData),
        headers: expect.objectContaining({
          'Content-Type': 'application/json',
        }),
      })
    );
    expect(result).toEqual(responseData);
  });

  it('should handle query parameters', async () => {
    (global.fetch as any).mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Map([['content-type', 'application/json']]),
      json: async () => ({ rows: [] }),
    });

    await client.get('/api/users', { limit: 25, offset: 0, status: 'ACTIVE' });

    expect(global.fetch).toHaveBeenCalledWith(
      'http://localhost:8080/api/users?limit=25&offset=0&status=ACTIVE',
      expect.any(Object)
    );
  });

  it('should handle errors', async () => {
    (global.fetch as any).mockResolvedValueOnce({
      ok: false,
      status: 404,
      statusText: 'Not Found',
      headers: new Map(),
      text: async () => JSON.stringify({ error: 'User not found' }),
    });

    await expect(client.get('/api/users/999')).rejects.toMatchObject({
      message: expect.stringContaining('404'),
      status: 404,
    });
  });

  it('should apply request interceptors', async () => {
    const interceptor: Interceptor = {
      name: 'test',
      onRequest: (config) => {
        config.headers = {
          ...config.headers,
          'X-Test': 'value',
        };
        return config;
      },
    };

    client.interceptor.use(interceptor);

    (global.fetch as any).mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Map([['content-type', 'application/json']]),
      json: async () => ({}),
    });

    await client.get('/api/test');

    expect(global.fetch).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({
        headers: expect.objectContaining({
          'X-Test': 'value',
        }),
      })
    );
  });

  it('should apply response interceptors', async () => {
    const interceptor: Interceptor = {
      name: 'transform',
      onResponse: (response, data) => {
        return { ...data, transformed: true };
      },
    };

    client.interceptor.use(interceptor);

    (global.fetch as any).mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Map([['content-type', 'application/json']]),
      json: async () => ({ id: 1 }),
    });

    const result = await client.get('/api/test');

    expect(result).toEqual({ id: 1, transformed: true });
  });

  it('should apply error interceptors', async () => {
    const errorHandler = vi.fn();
    const interceptor: Interceptor = {
      name: 'errorHandler',
      onError: errorHandler,
    };

    client.interceptor.use(interceptor);

    (global.fetch as any).mockResolvedValueOnce({
      ok: false,
      status: 500,
      statusText: 'Internal Server Error',
      headers: new Map(),
      text: async () => '',
    });

    await expect(client.get('/api/test')).rejects.toThrow();
    expect(errorHandler).toHaveBeenCalled();
  });
});

describe('InterceptorManager', () => {
  let manager: InterceptorManager;

  beforeEach(() => {
    manager = new InterceptorManager();
  });

  it('should add and remove interceptors', () => {
    const interceptor: Interceptor = { name: 'test' };

    const unsubscribe = manager.use(interceptor);
    expect(manager.getAll()).toHaveLength(1);

    unsubscribe();
    expect(manager.getAll()).toHaveLength(0);
  });

  it('should remove interceptor by name', () => {
    manager.use({ name: 'test1' });
    manager.use({ name: 'test2' });

    expect(manager.remove('test1')).toBe(true);
    expect(manager.getAll()).toHaveLength(1);
    expect(manager.getAll()[0].name).toBe('test2');
  });

  it('should clear all interceptors', () => {
    manager.use({ name: 'test1' });
    manager.use({ name: 'test2' });

    manager.clear();
    expect(manager.getAll()).toHaveLength(0);
  });

  it('should apply request interceptors in order', async () => {
    const order: string[] = [];

    manager.use({
      name: 'first',
      onRequest: (config) => {
        order.push('first');
        return config;
      },
    });

    manager.use({
      name: 'second',
      onRequest: (config) => {
        order.push('second');
        return config;
      },
    });

    await manager.applyRequestInterceptors({ url: '/test' });
    expect(order).toEqual(['first', 'second']);
  });

  it('should abort request if interceptor returns null', async () => {
    manager.use({
      name: 'abort',
      onRequest: () => null,
    });

    const result = await manager.applyRequestInterceptors({ url: '/test' });
    expect(result).toBeNull();
  });
});

