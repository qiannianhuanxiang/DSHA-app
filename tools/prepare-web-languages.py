#!/usr/bin/env python3
"""Generate ten-language DSH UI dictionaries and exact signed locale recipes.

No upstream installation, translation service, or user document is read here.
Extraction accepts static literals from the lock-bound raw npm fixture and the
current signed production patch chain, excluding this recipe's own dictionaries.
"""
import argparse
from collections import Counter
import hashlib
import importlib.util
import json
from pathlib import Path
import re
from source_text import write_text, matches_text
from web_language_catalog import EXTRA_LANGUAGES, extract
from runtime_stable_patches import load_chains, apply
from test_runtime_fixture import runtime as verified_runtime

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
MODULE = '@deepseek-ai/dsh-client-locale/lib/client.js'
SOURCE_SHA256 = 'bad8fe9bfb07af2a60c80933be65ceafdaab5386f404fb264401c7e540a36e16'
VERSION = '0.2.1-rc.3'


def official_catalog(runtime):
    runtime = Path(runtime)
    lock = (ROOT / 'tools/dsh-runtime/package-lock.json').read_bytes()
    if (runtime / 'package-lock.json').read_bytes() != lock:
        raise ValueError('WEB_LANGUAGE_LOCK_CHANGED')
    package = json.loads((runtime / 'node_modules/@deepseek-ai/dsh/package.json').read_text(encoding='utf8'))
    if package['name'] != '@deepseek-ai/dsh' or package['version'] != VERSION:
        raise ValueError('WEB_LANGUAGE_PACKAGE_GENERATION')
    spec = importlib.util.spec_from_file_location('web_language_runtime_builder', ROOT / 'tools/build-dsh-runtime.py')
    builder = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(builder)
    chains, _ = load_chains(ASSETS, VERSION)
    def transform(relative, data):
        prepared = builder.patched_content(Path(relative), data)
        chain = [row for row in chains.get(relative, []) if row[0] != 'language-patch.json']
        return apply(prepared, chain)
    return extract(runtime / 'node_modules', transform)


def read_translations(catalog):
    messages = catalog['messages']
    expected = {row['id'] for row in messages}
    result = {}
    for tag in EXTRA_LANGUAGES:
        data = json.loads((ROOT / 'tools/i18n/web-translations' / (tag + '.json')).read_text(encoding='utf8'))
        if set(data) != expected:
            raise ValueError('WEB_TRANSLATION_IDS: ' + tag)
        for row in messages:
            value = data[row['id']]
            format_separator = row['id'] == 'chat:number.groupSeparator' and value in (' ', '\u00a0', '\u202f', ',', '.')
            if not isinstance(value, str) or '\0' in value or (not value.strip() and row['en'].strip() and not format_separator):
                raise ValueError('WEB_TRANSLATION_TEXT: ' + tag + ':' + row['id'])
            if Counter(re.findall(r'\{\w+\}', value)) != Counter(re.findall(r'\{\w+\}', row['en'])):
                raise ValueError('WEB_TRANSLATION_ARGUMENTS: ' + tag + ':' + row['id'])
        result[tag] = data
    return result


def validate_source(catalog):
    if catalog.get('schema') != 1 or catalog.get('dshVersion') != VERSION \
            or catalog.get('inputs', {}).get(MODULE) != SOURCE_SHA256:
        raise ValueError('WEB_LANGUAGE_SOURCE_GENERATION')
    ids = set()
    for row in catalog['messages']:
        if row['id'] != row['namespace'] + ':' + row['key'] or row['id'] in ids \
                or not isinstance(row['zh'], str) or not isinstance(row['en'], str):
            raise ValueError('WEB_LANGUAGE_SOURCE_IDS')
        ids.add(row['id'])
    return catalog


def outputs(catalog, translations):
    groups = {}
    for row in catalog['messages']:
        groups.setdefault(row['namespace'], []).append([row['key'], [translations[tag][row['id']] for tag in EXTRA_LANGUAGES]])
    packed = json.dumps(list(groups.items()), ensure_ascii=False, separators=(',', ':'))
    template = (ROOT / 'tools/i18n/web-language-bridge.js').read_text(encoding='utf8')
    if template.count('/* DSHA_WEB_CATALOG */') != 1:
        raise ValueError('WEB_LANGUAGE_TEMPLATE_ANCHOR')
    helper = template.replace('/* DSHA_WEB_CATALOG */', packed)
    if len(helper.encode('utf8')) > 8 * 1024 * 1024:
        raise ValueError('WEB_LANGUAGE_HELPER_LIMIT')
    tags = json.dumps(['zh', 'zh-Hant', 'en', 'ru', 'ja', 'ko', 'ar', 'pt', 'la', 'fr'])
    lock = json.loads((ROOT / 'tools/dsh-runtime/package-lock.json').read_text(encoding='utf8'))
    recipe = dict(dshVersion=VERSION, module=MODULE, patches=[
        dict(before='ctx.provide("locale", locale);',
             after='ctx.provide("locale", locale);\n            ctx.effect(() => installDshaLanguageBridge(locale), "DSHA application language");',
             prependAsset='web-integration/language.js'),
        dict(before='resolveActive() {\n\t\t\t\tif (this.preference === void 0) return this.provisional;',
             after='resolveActive() {\n                if (typeof window !== "undefined" && ' + tags + '.includes(window.__DSHA_LANGUAGE__) && this.catalog.has(localeKey(window.__DSHA_LANGUAGE__))) return window.__DSHA_LANGUAGE__;\n\t\t\t\tif (this.preference === void 0) return this.provisional;'),
        dict(before='this.preference = match.id;\n\t\t\t\tif (this.snapshot.active !== match.id)',
             after='this.preference = match.id;\n                if (typeof window !== "undefined" && window.__DSHA_LANGUAGE__ && window.__DSHA_LANGUAGE__ !== match.id) { window.__DSHA_LANGUAGE__ = match.id; window.dispatchEvent(new CustomEvent("dsha-language-selected", { detail: match.id })); }\n\t\t\t\tif (this.snapshot.active !== match.id)')
    ], sourceSha256=SOURCE_SHA256,
        packageIntegrity=lock['packages']['node_modules/@deepseek-ai/dsh-client-locale']['integrity'])
    previous = json.loads((ROOT / 'tools/fixtures/language-v1-patch.json').read_text(encoding='utf8'))
    if previous.get('sourceSha256') != SOURCE_SHA256 or previous.get('module') != MODULE:
        raise ValueError('WEB_LANGUAGE_PREVIOUS_RECIPE_GENERATION')
    recipe['previousPatches'] = previous['patches']
    text = json.dumps(recipe, ensure_ascii=False, indent=2) + '\n'
    return {'web-integration/language.js': helper, 'language-patch.json': text,
            'recovery-recipes/' + VERSION + '/language-patch.json': text}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime', type=Path)
    parser.add_argument('--extract', action='store_true', help='write the source catalog only; never translates it')
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--catalog-only', action='store_true', help='offline build check of fixed catalog and generated outputs; no upstream fixture required')
    args = parser.parse_args()
    source = ROOT / 'tools/i18n/web-messages.json'
    if args.catalog_only:
        if not args.check or args.extract:
            parser.error('--catalog-only requires --check and cannot extract')
        catalog = validate_source(json.loads(source.read_text(encoding='utf8')))
    else:
        runtime = args.runtime or Path(json.loads((ROOT / 'app/build/test-runtimes/current.json').read_text(encoding='utf8'))['raw'])
        runtime = verified_runtime('raw', runtime)
        actual = hashlib.sha256((runtime / 'node_modules' / MODULE).read_bytes()).hexdigest()
        if actual != SOURCE_SHA256:
            raise ValueError('WEB_LANGUAGE_OFFICIAL_SOURCE_CHANGED')
        catalog = validate_source(official_catalog(runtime))
    encoded = json.dumps(catalog, ensure_ascii=False, indent=2) + '\n'
    if args.extract:
        write_text(source, encoded)
        print('Extracted ' + str(len(catalog['messages'])) + ' locked UI messages')
        return
    if not matches_text(source, encoded):
        raise ValueError('WEB_LANGUAGE_SOURCE_CATALOG_STALE')
    generated = outputs(catalog, read_translations(catalog))
    for name, content in generated.items():
        target = ASSETS / name
        if args.check:
            if not matches_text(target, content):
                raise ValueError('WEB_LANGUAGE_GENERATED_ASSET_STALE: ' + name)
        else:
            write_text(target, content)
    print('PASS ' + str(len(catalog['messages'])) + ' DSH UI messages x 10 languages; ' + str(len(generated['web-integration/language.js'].encode('utf8'))) + ' helper bytes')


if __name__ == '__main__':
    main()
