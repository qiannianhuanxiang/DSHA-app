/* DSHA_UI_LANGUAGES_V2: fixed UI dictionaries only; user text stays verbatim. */
function installDshaLanguageBridge(locale) {
    if (typeof window === 'undefined' || typeof document === 'undefined') return () => {};
    const languages = [
        ['zh-Hant', '繁體中文'], ['ru', 'Русский'], ['ja', '日本語'], ['ko', '한국어'],
        ['ar', 'العربية'], ['pt', 'Português'], ['la', 'Latina'], ['fr', 'Français']
    ];
    const valid = value => ['zh', 'en', ...languages.map(row => row[0])].includes(value);
    const disposers = [];
    try {
        for (const [id, label] of languages) {
            if (!locale.getSnapshot().locales.some(entry => entry.id === id))
                disposers.push(locale.addLanguage({ id, label, fallback: id === 'zh-Hant' ? 'zh' : 'en' }));
        }
        // Generated from namespaced dictionaries of the exact locked rc3 bundles.
        // Each row is [key, [translations in the language order declared above]].
        const catalog = /* DSHA_WEB_CATALOG */;
        for (const [namespace, entries] of catalog) {
            const dictionaries = Object.fromEntries(languages.map(([id], index) => [id,
                Object.fromEntries(entries.map(([key, translated]) => [key, translated[index]]))]));
            disposers.push(locale.register(namespace, dictionaries));
        }
        const syncDocument = () => {
            const active = locale.getSnapshot().active;
            if (!valid(active)) return;
            document.documentElement.lang = active;
            document.documentElement.dir = active === 'ar' ? 'rtl' : 'ltr';
        };
        const apply = () => {
            const language = window.__DSHA_LANGUAGE__;
            if (!valid(language)) return;
            if (locale.getSnapshot().active !== language) locale.setLocale(language);
            syncDocument();
        };
        disposers.push(locale.subscribe(syncDocument));
        window.addEventListener('dsha-language', apply);
        disposers.push(() => window.removeEventListener('dsha-language', apply));
        apply();
        syncDocument();
    } catch (error) {
        for (const dispose of disposers.reverse()) dispose();
        throw error;
    }
    return () => { for (const dispose of disposers.reverse()) dispose(); };
}
