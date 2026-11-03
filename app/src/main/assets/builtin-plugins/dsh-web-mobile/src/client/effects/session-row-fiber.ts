// session-row-fiber.ts — DOM-free core behind the drawer session-row tap
// fallback (#49 / PR #49): on some WebKit/iOS builds a tap on a session row
// never produces a `click`, so the row's own React onClick never runs and
// navigation silently does nothing. The runtime half resolves the tapped
// element's fiber with `reactFiberOf`, walks it with `findSessionIdInFiber`,
// and hands the id to the host. This file is the PURE half, so the walk is
// unit-testable with no DOM, no renderer and no DSH runtime.
//
// Deliberately has ZERO import statements, like reconciler-core.ts: node:test
// loads it directly through Node's native type stripping, and the client bundle
// has nothing to resolve. Nothing here touches `document` or `window`.
//
// Two measured facts from the live 0.1.5-rc.2 host shaped the walk:
//  - the row ITEM fiber is 3 hops above the tapped element and carries the
//    session id at `props.node.id`;
//  - hop 32 is a `ScopeProvider` ancestor whose `props.scope` is the literal
//    string 'session-maybe'. Anything trusting the SHAPE of an id
//    (/^session[-_]/) would hand that string to `ctx.sessions.open(id)`, and
//    that contract fails loud on unknown ids. So a value counts only when the
//    caller's `isKnownId` accepts it — there is no shape-based fallback, and a
//    "plausible looking id" must never be guessed.

/** The only part of a React fiber this walk reads. */
export interface FiberNodeLike {
  memoizedProps?: Record<string, unknown> | null
  return?: FiberNodeLike | null
}

/**
 * Hop budget for one walk. The measured row item fiber is 3 hops up and the
 * whole chain is well under this; the bound exists so an unexpectedly deep or
 * malformed chain cannot spin.
 */
export const FIBER_WALK_LIMIT = 60

/** Props keys holding an OBJECT with an `.id` (row items, session records). */
const OBJECT_KEYS = ['node', 'session', 'summary', 'result'] as const
/** Props keys holding the id STRING directly. */
const ID_KEYS = ['sessionId', 'id'] as const
/** React stamps a fiber on a node under a per-renderer random suffix. */
const FIBER_KEY_PREFIXES = ['__reactFiber$', '__reactInternalInstance$'] as const

/** `value.id` when `value` is an object carrying a string id, else null. */
function objectIdOf(value: unknown): string | null {
  if (typeof value !== 'object' || value === null) return null
  const id = (value as { id?: unknown }).id
  return typeof id === 'string' ? id : null
}

/** The first id this hop's props offer that `isKnownId` accepts. */
function acceptedIdInProps(
  props: Record<string, unknown> | null | undefined,
  isKnownId: (id: string) => boolean,
): string | null {
  if (props === null || props === undefined) return null
  for (const key of OBJECT_KEYS) {
    const candidate = objectIdOf(props[key])
    if (candidate !== null && isKnownId(candidate)) return candidate
  }
  for (const key of ID_KEYS) {
    const candidate = props[key]
    if (typeof candidate === 'string' && isKnownId(candidate)) return candidate
  }
  return null
}

/**
 * Walk from `fiber` towards the root (`.return`) and return the session id of
 * the NEAREST hop offering one the caller knows. A hop whose candidate is
 * rejected does not stop the walk, so an outer row item fiber still wins over
 * an inner fiber carrying an unrelated or stale id. Returns null when nothing
 * within `limit` hops (the starting fiber counts as the first) is accepted.
 */
export function findSessionIdInFiber(
  fiber: FiberNodeLike | null | undefined,
  isKnownId: (id: string) => boolean,
  limit: number = FIBER_WALK_LIMIT,
): string | null {
  let hop: FiberNodeLike | null | undefined = fiber
  for (let walked = 0; walked < limit && hop !== null && hop !== undefined; walked += 1) {
    const found = acceptedIdInProps(hop.memoizedProps, isKnownId)
    if (found !== null) return found
    hop = hop.return
  }
  return null
}

/**
 * The React fiber a DOM node (or any renderer-stamped object) carries: React
 * assigns it under `__reactFiber$<rendererKey>` and keeps the legacy
 * `__reactInternalInstance$<rendererKey>` alias. First stamp wins; null when
 * the value is not a stamped object.
 */
export function reactFiberOf(instance: object | null | undefined): FiberNodeLike | null {
  if (instance === null || instance === undefined) return null
  if (typeof instance !== 'object') return null
  const record = instance as Record<string, unknown>
  for (const prefix of FIBER_KEY_PREFIXES) {
    for (const key of Object.keys(record)) {
      if (!key.startsWith(prefix)) continue
      const fiber = record[key]
      if (typeof fiber === 'object' && fiber !== null) return fiber as FiberNodeLike
    }
  }
  return null
}

/**
 * Whether a pointer release still counts as a tap: it stayed within `slopPx` on
 * BOTH axes (max-norm, not Euclidean). The drawer list scrolls vertically, so a
 * 60px vertical drift must not navigate.
 */
export function isTapWithinSlop(
  from: { x: number; y: number },
  to: { x: number; y: number },
  slopPx: number,
): boolean {
  return Math.abs(to.x - from.x) <= slopPx && Math.abs(to.y - from.y) <= slopPx
}
