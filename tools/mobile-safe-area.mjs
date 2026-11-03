// The native container owns bars/cutouts. Browser tabs retain CSS env() fallbacks.
export function applyMobileSafeArea(source) {
  return source.replace(/env\(safe-area-inset-(top|right|bottom|left),\s*0px\)/g,
    (original, side) => `var(--dsha-safe-area-inset-${side}, ${original})`);
}
