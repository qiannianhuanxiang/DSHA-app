// 插入固定 3.1.0 viewport-meta 的函数；不重复扣原生已消费的 IME/insets。
function syncStableViewportHeight() {
    const root = document.documentElement;
    let stableVh = 0, stableWidth = 0, stableValue = '';
    let lastVisibleVh = '', lastViewportTop = '', syncWidth = -1;
    let syncTimer = 0, syncRaf = 0, disposed = false;
    const writeViewport = () => {
        if (disposed) return;
        const height = window.innerHeight, width = window.innerWidth, visible = window.visualViewport;
        const nextVh = `${Math.max(1, Math.min(height, visible?.height ?? height))}px`;
        const nextTop = `${visible?.offsetTop ?? 0}px`;
        if (nextVh !== lastVisibleVh) {
            lastVisibleVh = nextVh;
            root.style.setProperty('--dsha-mobile-visible-vh', nextVh);
        }
        if (nextTop !== lastViewportTop) {
            lastViewportTop = nextTop;
            root.style.setProperty('--dsha-mobile-viewport-top', nextTop);
        }
        if (stableVh === 0 || height > stableVh || width !== stableWidth) {
            stableVh = height;
            stableWidth = width;
            const next = `${height}px`;
            if (next !== stableValue) { stableValue = next; root.style.setProperty(exports.STABLE_VIEWPORT_VAR, next); }
        }
    };
    const syncViewport = () => {
        if (disposed) return;
        const width = window.innerWidth;
        if (width !== syncWidth) {
            syncWidth = width;
            if (syncTimer) { clearTimeout(syncTimer); syncTimer = 0; }
            if (syncRaf) { cancelAnimationFrame(syncRaf); syncRaf = 0; }
            writeViewport();
            return;
        }
        if (syncTimer) clearTimeout(syncTimer);
        syncTimer = setTimeout(() => {
            syncTimer = 0;
            if (disposed || syncRaf) return;
            syncRaf = requestAnimationFrame(() => { syncRaf = 0; writeViewport(); });
        }, 150);
    };
    syncViewport();
    window.addEventListener('resize', syncViewport);
    window.visualViewport?.addEventListener('resize', syncViewport);
    window.visualViewport?.addEventListener('scroll', syncViewport);
    return () => {
        disposed = true;
        if (syncTimer) clearTimeout(syncTimer);
        if (syncRaf) cancelAnimationFrame(syncRaf);
        window.removeEventListener('resize', syncViewport);
        window.visualViewport?.removeEventListener('resize', syncViewport);
        window.visualViewport?.removeEventListener('scroll', syncViewport);
        root.style.removeProperty('--dsha-mobile-visible-vh');
        root.style.removeProperty('--dsha-mobile-viewport-top');
        root.style.removeProperty(exports.STABLE_VIEWPORT_VAR);
    };
}
