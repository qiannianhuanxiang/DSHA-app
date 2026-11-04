// The layout service face drifted between host generations. rc.6's ILayout
// carries only toggleSidebar/openDetails/closeDetails; 0.1.6-alpha.2 added
// selectPanel(panelId | null) for the sidebar's main-area panels. This helper
// lets call sites stay compile-green against rc.6 typings while degrading
// explicitly on a host that cannot select a panel, instead of throwing.
//
// Kept DOM-free and dependency-free (like sessions-compat.ts) so it is directly
// unit-testable and compiles against either generation's typings — never import
// the layout service types here.

/** Structural view across generations. */
interface LayoutLike {
  selectPanel?: unknown
}

/**
 * The "leave the panel" action, or null when the host has no panel-selection
 * API at all (rc.6).
 *
 * The sidebar panel list is an alpha.2-era surface, so on rc.6 nothing arms
 * this — but the capability is probed rather than assumed, because a plugin
 * that throws on an older host is worse than one that goes inert.
 *
 * @param layout - `ctx.layout` as handed to the plugin.
 * @returns a no-argument action calling `selectPanel(null)`, or null.
 */
export function panelSelectorOf(layout: unknown): (() => void) | null {
  if (typeof layout !== 'object' || layout === null) return null
  const select = (layout as LayoutLike).selectPanel
  if (typeof select !== 'function') return null
  return (): void => {
    ;(select as (panelId: null) => void).call(layout, null)
  }
}
