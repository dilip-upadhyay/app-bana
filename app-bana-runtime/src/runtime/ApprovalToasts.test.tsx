/**
 * ApprovalToasts.test.tsx — Task C3.8.
 */
import { describe, it, expect } from 'vitest';
import { ApprovalConflictError } from '@appbana/shared';
import { describeSubmitFailure } from './approval-toasts';

class HttpError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
  }
}

describe('describeSubmitFailure', () => {
  it('names a conflict as an already-submitted record, not a failure', () => {
    const msg = describeSubmitFailure(new ApprovalConflictError('Record is already PENDING'));
    expect(msg).toContain('already submitted');
  });

  it('explains a 403 as a permission problem in plain words', () => {
    const msg = describeSubmitFailure(new HttpError('forbidden: not a maker', 403));
    expect(msg).toBe('You do not have permission to submit this for approval.');
    expect(msg).not.toContain('403');
  });

  it('explains a 401 as an expired session', () => {
    expect(describeSubmitFailure(new HttpError('unauthorized', 401))).toContain('session expired');
  });

  it('passes a server message through when it has nothing better to say', () => {
    expect(describeSubmitFailure(new Error('Submit failed'))).toBe('Submit failed.');
  });

  it('does not double up terminal punctuation', () => {
    expect(describeSubmitFailure(new Error('Submit failed.'))).toBe('Submit failed.');
    expect(describeSubmitFailure(new Error('Really?'))).toBe('Really?');
  });

  /** The sentence gets concatenated with a reassurance, so it must terminate. */
  it('always produces a terminated sentence', () => {
    const cases: unknown[] = [
      new ApprovalConflictError('x'),
      new HttpError('x', 403),
      new HttpError('x', 401),
      new Error('boom'),
      new Error('   '),
      null,
      undefined,
      'a bare string',
    ];
    for (const c of cases) {
      expect(describeSubmitFailure(c)).toMatch(/[.!?]$/);
    }
  });

  it('falls back to a generic sentence for a non-Error throw', () => {
    expect(describeSubmitFailure(null)).toBe('It could not be submitted for approval.');
    expect(describeSubmitFailure('a bare string')).toBe('It could not be submitted for approval.');
  });

  it('falls back when the error message is only whitespace', () => {
    expect(describeSubmitFailure(new Error('   '))).toBe('It could not be submitted for approval.');
  });
});
