/**
 * hardening-h1-file-tenant-isolation.spec.ts
 *
 * H1 hardening — files uploaded by tenant A must never be downloadable
 * by tenant B, even if B correctly guesses the fileId. Before H1, the
 * `SELECT_SQL` on `/api/files/{tenantId}/{appId}/{fileId}` only matched
 * on `file_id`, so a hostile tenant could enumerate/download any file
 * once they knew or guessed a UUID.
 *
 * Wire contract locked in here:
 *   - Owner tenant + app pair can download the file (200 + bytes)
 *   - Wrong tenantId in the URL returns 404 (not 403 — 403 would confirm
 *     the file exists, 404 refuses to leak existence)
 *   - Wrong appId in the URL returns 404
 *   - Upload response includes a `url` field so the runtime doesn't have
 *     to reconstruct the path (used by FileUploadField.tsx)
 */
import { expect, test } from '@playwright/test';
import {
  BACKEND_URL,
  healthCheck,
  newHardeningFixture,
  disposeFixture,
  type HardeningFixture,
} from './fixtures';

test.describe('H1 — file download tenant isolation', () => {
  test.beforeAll(async () => {
    if (!(await healthCheck())) {
      test.skip(true, `Backend unreachable at ${BACKEND_URL} — start the stack first`);
    }
  });

  test('owner can download; foreign tenant/app returns 404', async () => {
    const alice = await newHardeningFixture('h1-alice');
    const bob = await newHardeningFixture('h1-bob');
    if (!alice || !bob) { test.skip(true, 'Registration rate-limited'); return; }

    try {
      // Alice uploads a file. Backend generates fileId.
      const fileBody = 'hello from alice';
      const upload = await alice.api.post(
        `${BACKEND_URL}/api/files/${alice.tenantId}/${alice.appId}/upload`,
        {
          headers: { Authorization: `Bearer ${alice.token}` },
          multipart: {
            file: {
              name: 'alice.txt',
              mimeType: 'text/plain',
              buffer: Buffer.from(fileBody, 'utf8'),
            },
          },
        },
      );
      expect(upload.status(), await upload.text()).toBeLessThan(300);
      const meta = await upload.json();
      const fileId = String(meta.fileId ?? meta.id ?? '');
      expect(fileId, 'upload must return a fileId').not.toBe('');
      // Response contract: `url` field is what the runtime displays.
      expect(typeof meta.url).toBe('string');
      expect(meta.url).toContain(fileId);

      // 1. Owner can read.
      const okRes = await alice.api.get(
        `${BACKEND_URL}/api/files/${alice.tenantId}/${alice.appId}/${fileId}`,
        { headers: { Authorization: `Bearer ${alice.token}` } },
      );
      expect(okRes.status()).toBe(200);
      expect(await okRes.text()).toBe(fileBody);

      // 2. Foreign tenant is blocked with 404 (not 403 — no existence leak).
      const foreignTenantRes = await bob.api.get(
        `${BACKEND_URL}/api/files/${bob.tenantId}/${alice.appId}/${fileId}`,
        { headers: { Authorization: `Bearer ${bob.token}` } },
      );
      expect(foreignTenantRes.status(),
        `bob (${bob.tenantId}) must not be able to fetch alice's (${alice.tenantId}) file`,
      ).toBe(404);

      // 3. Same tenant but wrong appId is also blocked.
      const wrongAppRes = await alice.api.get(
        `${BACKEND_URL}/api/files/${alice.tenantId}/wrong-app-id/${fileId}`,
        { headers: { Authorization: `Bearer ${alice.token}` } },
      );
      expect(wrongAppRes.status()).toBe(404);
    } finally {
      await disposeFixture(alice);
      await disposeFixture(bob);
    }
  });
});
