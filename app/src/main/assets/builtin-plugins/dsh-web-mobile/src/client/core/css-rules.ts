// css-rules.ts — DOM-free reader for the mobile plugin's own stylesheets,
// written for the SOURCE-LEVEL guards in tests/ (e.g. "the message text family
// never pins a hardcoded px font-size again"). Deliberately has ZERO import
// statements, like reconciler-core.ts: the caller passes the stylesheet string
// in, so node:test can exercise it with no DOM, no browser and no DSH runtime.
//
// This is not a CSS engine and does not pretend to cascade. Documented limits:
//  - at-rule CONDITIONS are not evaluated and not even kept: a rule inside
//    @media / @supports / @layer is reported with its own selector only, so a
//    guard reading these blocks cannot tell whether the rule is mobile-only;
//  - `@keyframes` steps are dropped (they are not selectors) and so are
//    `@import …;` at-statements; any other nested at-rule is treated like
//    @media — descended into, its prelude dropped;
//  - the selector surface is deliberately tiny: type selectors, [class*="…"]
//    arms and :has(<tag>) are evaluated, :not(…) groups are removed, and every
//    other arm ([data-…] presence, .dot-classes, :first-child, other attribute
//    operators) cannot be falsified by a descriptor — as a leading token it is
//    dropped (assumed satisfied), as an arm's SUBJECT it makes that arm
//    unavailable. A class-fragment arm is read with or without inner spaces
//    ([ class*="…" ] is the wrapped spelling this repo uses for long arms).
//    Combinators (>, +, ~) are dropped, so child and descendant are one
//    relation here;
//  - only a `font-size` longhand declaration is read: a px size hidden in a
//    `font:` shorthand is invisible to this reader, and so is `font-size`
//    written with whitespace before the colon (`font-size : 15px`);
//  - a `{` inside a declaration value (custom property, odd string) makes that
//    one rule unreadable — a bogus prelude is reported instead of its selector
//    — while the rest of the sheet still parses.

export type CssElementDescriptor = {
  tag: string
  classes?: string[]
  ancestors?: CssElementDescriptor[]
}

export type CssRuleBlock = { selector: string; body: string }

const COMMENT = /\/\*[\s\S]*?\*\//g
/** Keyframe steps (`from`, `50%`) are not selectors; two brace levels deep. */
const KEYFRAMES = /@(?:-webkit-)?keyframes\b[^{]*\{(?:[^{}]|\{[^{}]*\})*\}/gi
/** `@import …;` / `@layer a, b;` leave no block but would land in the next prelude. */
const AT_STATEMENT = /@[a-z-]+[^{};]*;/gi
/** Innermost `prelude { body }` pairs — bodies are brace-free CSS. */
const BLOCK = /([^{}]*)\{([^{}]*)\}/g

/**
 * Selector/body pairs from a stylesheet string, at every nesting level: the
 * conditional at-rules (@media / @supports / @layer) that wrap the mobile
 * rules are descended into, which is the whole point — the message text rule
 * lives inside `@media (hover: none), (pointer: coarse)`, so a reader that
 * skipped at-rule blocks would report nothing and its guard would be green
 * forever.
 */
export function findRuleBlocks(css: string): CssRuleBlock[] {
  const source = css.replace(COMMENT, ' ').replace(KEYFRAMES, ' ').replace(AT_STATEMENT, ' ')
  const blocks: CssRuleBlock[] = []
  for (const match of source.matchAll(BLOCK)) {
    // The prelude is the text since the previous brace: for a rule nested in a
    // conditional at-rule that is the rule's own selector (the @media prelude
    // sits before an earlier brace, which is why it is dropped), and a nested
    // at-rule prelude starts with '@' and is dropped with it.
    const selector = (match[1] ?? '').trim().replace(/\s+/g, ' ')
    if (selector === '' || selector.startsWith('@')) continue
    blocks.push({ selector, body: (match[2] ?? '').trim() })
  }
  return blocks
}

const COMBINATOR = new Set(['>', '+', '~'])
const NOT_GROUP = /:not\((?:[^()]|\([^()]*\))*\)/g
const HAS_TAG = /:has\(\s*([a-z][a-z0-9]*)\s*\)/g
const CLASS_FRAGMENT = /\[\s*class\*=\s*["']?([^"'\]\s]+)["']?\s*\]/g
const HAS_CLASS_FRAGMENT = /\[\s*class\*=/
const TYPE_SELECTOR = /^[a-z][a-z0-9]*/

/** One compound selector per whitespace-separated token, combinators dropped. */
function tokensOf(arm: string): string[] {
  return arm
    .replace(NOT_GROUP, '')
    // Never split inside a `(…)` or `[…]` group: this repo wraps long class
    // arms as `[ class*="…" ]`, and those inner spaces belong to one token.
    .split(/\s+(?![^()]*\))(?![^\[]*\])/)
    .filter((token) => token !== '' && !COMBINATOR.has(token))
}

/** Whether the descriptor can falsify this token at all (type / [class*=…]). */
function isCheckable(token: string): boolean {
  return TYPE_SELECTOR.test(token) || HAS_CLASS_FRAGMENT.test(token)
}

function matchesToken(
  token: string,
  el: CssElementDescriptor,
  below: readonly CssElementDescriptor[],
): boolean {
  const type = token.match(TYPE_SELECTOR)
  if (type !== null && (type[0] ?? '') !== el.tag) return false
  const classes = el.classes ?? []
  for (const fragment of token.matchAll(CLASS_FRAGMENT)) {
    const needle = fragment[1] ?? ''
    if (!classes.some((cls) => cls.includes(needle))) return false
  }
  // `:has(p)` is approximated as "a later element on the described path is a
  // p" — the descriptor lists the descendants that matter, so no tree is
  // needed. Any other `:has(…)` argument cannot be checked and is ignored.
  for (const has of token.matchAll(HAS_TAG)) {
    const tag = has[1] ?? ''
    if (!below.some((descendant) => descendant.tag === tag)) return false
  }
  return true
}

function matchesChain(
  tokens: readonly string[],
  index: number,
  path: readonly CssElementDescriptor[],
  from: number,
): boolean {
  if (index === tokens.length) return true
  const token = tokens[index] ?? ''
  for (let i = from; i < path.length; i += 1) {
    const el = path[i]
    if (el === undefined) continue
    if (matchesToken(token, el, path.slice(i + 1)) && matchesChain(tokens, index + 1, path, i + 1)) {
      return true
    }
  }
  return false
}

/**
 * Whether `el`'s described path (its ancestors, outermost first, then itself)
 * can satisfy the selector. The subject of an arm — its rightmost token — may
 * be any element on that path, because a font-size declared on the message
 * column is inherited by the text inside it, so both belong to one family.
 */
export function matchesSelectorText(selector: string, el: CssElementDescriptor): boolean {
  const path = [...(el.ancestors ?? [])].reverse().concat(el)
  for (const arm of selector.split(/,(?![^()]*\))/)) {
    const tokens = tokensOf(arm)
    // Tokens the descriptor cannot falsify (the frame marker, :first-child, a
    // bare [data-…] arm) describe ancestors ABOVE the described path — the
    // fixture only lists the tail it knows about — so drop them.
    while (tokens.length > 1 && !isCheckable(tokens[0] ?? '')) tokens.shift()
    const subject = tokens[tokens.length - 1]
    if (subject === undefined || !isCheckable(subject)) continue
    if (matchesChain(tokens, 0, path, 0)) return true
  }
  return false
}

/** Keywords that resolve against another rule instead of pinning a size. */
const SIZE_KEYWORD = /^(?:inherit|unset|revert|revert-layer)$/i

/**
 * The `font-size` a stylesheet declares for the family `el` stands for, with
 * the selector that declared it, or null when no matching rule declares one.
 * Throws when matching rules disagree — an ambiguous family is a guard bug,
 * not something to report as a pass.
 */
export function fontSizeFor(
  css: string,
  el: CssElementDescriptor,
): { value: string; selector: string } | null {
  const hits: Array<{ value: string; selector: string }> = []
  for (const block of findRuleBlocks(css)) {
    if (!matchesSelectorText(block.selector, el)) continue
    const declared = block.body.match(/font-size:\s*([^;]+)/)
    if (declared === null) continue
    const value = (declared[1] ?? '').trim()
    if (SIZE_KEYWORD.test(value.replace(/\s*!important\s*$/, ''))) continue
    hits.push({ value, selector: block.selector })
  }
  if (hits.length === 0) return null
  const values = new Set(hits.map((hit) => hit.value))
  if (values.size > 1) {
    throw new Error(`conflicting font-size for fixture: ${[...values].join(' | ')}`)
  }
  return hits[0] ?? null
}
