/**
 * RecordContext.tsx — Runtime record scope.
 *
 * When a page renders in the context of a single parent record (e.g. a
 * detail page for `Customer#42`), any nested {@link ChildTable} nodes
 * need the parent id in order to filter the child rows. Rather than
 * force the scaffolder to bake `parentId` into every child_table's
 * props (which is impossible — the id is only known at click time),
 * DetailPage wraps its rendered content in this provider and Renderer
 * reads it inside `case 'child_table':` when `props.parentId` is empty.
 *
 * H2 hardening (post-B4 review). See ChildTable and Renderer.
 */
import { createContext, useContext } from 'react';

export interface RecordScope {
  /** The parent record's primary-key value (usually numeric, always stringified for URL use). */
  readonly recordId: string;
  /** The parent entity's qualified key (`{tenant}_{app}_{Entity}`), if known. */
  readonly entityKey?: string;
}

const Ctx = createContext<RecordScope | null>(null);

export const RecordContextProvider = Ctx.Provider;

/** Returns the ambient record scope, or null when rendered outside a
 *  detail page (e.g. on a list page). Consumers should treat null as
 *  "no parent" and fall back to their own props. */
export function useRecordScope(): RecordScope | null {
  return useContext(Ctx);
}
