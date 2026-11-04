#!/usr/bin/env python3
"""Generate exact rc3 file-slot and owned image actions recipes from locked npm originals."""
import argparse
import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from source_text import write_text, matches_text

VERSION = '0.2.1-rc.3'
MODULES = {
    'file-deliverables-patch.json': ('@deepseek-ai/dsh-client-ui-deliverables/lib/client.js',
        '0b786f9c89e728ac33926b452536daaea678d9e7741a579126e406a232cb03ed'),
    'file-image-actions-patch.json': ('@deepseek-ai/dsh-client-ui-attachment/lib/client.js',
        'cdb497656ed0e66f011125df22791f6f579c85710d2d915020ddcfe6c0f58072'),
}


def generate(module_root, lock_path=None):
    lock = json.loads((lock_path or ROOT / 'tools/dsh-runtime/package-lock.json').read_text(encoding='utf8'))
    results = {}
    for asset, (module, digest) in MODULES.items():
        target = module_root / module
        data = target.read_bytes()
        if hashlib.sha256(data).hexdigest() != digest:
            raise ValueError('FILE_ACTIONS_OFFICIAL_SOURCE_CHANGED: ' + module)
        package_name = module.removesuffix('/lib/client.js')
        package = json.loads((target.parent.parent / 'package.json').read_text(encoding='utf8'))
        locked = lock['packages']['node_modules/' + package_name]
        if package['name'] != package_name or package['version'] != VERSION or locked['version'] != VERSION:
            raise ValueError('FILE_ACTIONS_PACKAGE_VERSION_CHANGED')
        source = data.decode('utf8')
        patches = []

        def replace(before, after):
            if source.count(before) != 1:
                raise ValueError('FILE_ACTIONS_ANCHOR_CHANGED: ' + before[:100])
            patches.append(dict(before=before, after=after))

        if asset == 'file-deliverables-patch.json':
            # Official PresentedFile has only path/description. Do not invent a
            # directory kind, infer from display strings, or decode action URLs.
            for slot in ['deliverables.file.actions', 'deliverables.review.file.actions']:
                anchor = 'renderSlot("' + slot + '", {\n'
                replace(anchor, anchor + '\t\t\t\t\t\t\t\t/* DSHA_FILE_DELIVERABLES_RC2_V1: exact owner coordinates. */\n'
                        + '\t\t\t\t\t\t\t\tsessionId,\n\t\t\t\t\t\t\t\tabsolutePath: file.path,\n')
            before = 'host !== null && host !== "error" && !host.available && (0, react_jsx_runtime.jsx)("span", {'
            replace(before, 'host !== null && host !== "error" && !host.available && typeof window.DSHA?.downloadArtifact !== "function" && (0, react_jsx_runtime.jsx)("span", {')
            before = 'host === "error" && (0, react_jsx_runtime.jsxs)("div", {'
            replace(before, 'host === "error" && typeof window.DSHA?.downloadArtifact !== "function" && (0, react_jsx_runtime.jsxs)("div", {')
        else:
            before = '\t\t\t\t"aria-busy": loadingThumbnail || void 0,\n\t\t\t\tonClick: () => {\n\t\t\t\t\tif (src !== null) setOpen(true);\n\t\t\t\t},'
            after = '''\t\t\t\t"aria-busy": loadingThumbnail || void 0,
\t\t\t\t// DSHA_IMAGE_ACTIONS_RC2_V1: this owned image's original authorized Blob URL.
\t\t\t\tonContextMenu: event => {
\t\t\t\t\tif (window.DSHAFileTransfer?.imageMenu(src, label)) { event.preventDefault(); event.stopPropagation(); }
\t\t\t\t},
\t\t\t\tonPointerDown: event => window.DSHAFileTransfer?.imageDown(event, src, label),
\t\t\t\tonPointerMove: event => window.DSHAFileTransfer?.imageMove(event),
\t\t\t\tonPointerUp: () => window.DSHAFileTransfer?.imageEnd(),
\t\t\t\tonPointerCancel: () => window.DSHAFileTransfer?.imageEnd(),
\t\t\t\tonClick: event => {
\t\t\t\t\tif (window.DSHAFileTransfer?.imageClick(event)) return;
\t\t\t\t\tif (src !== null) setOpen(true);
\t\t\t\t},'''
            replace(before, after)
            before = '\t\t\t\tonClose: close\n\t\t\t})] });'
            after = '''\t\t\t\tonClose: close
\t\t\t}), open && src !== null && typeof window.DSHA?.saveImage === "function" && (0, react_dom.createPortal)((0, react_jsx_runtime.jsx)("button", {
\t\t\t\ttype: "button", "data-dsha-image-save": true,
\t\t\t\tonClick: () => window.DSHAFileTransfer?.imageMenu(src, label),
\t\t\t\tchildren: document.documentElement.lang?.startsWith("zh") ? "保存到相册" : "Save to gallery"
\t\t\t}), document.body)] });'''
            replace(before, after)
        results[asset] = json.dumps(dict(version=1, dshVersion=VERSION, module=module,
            sourceSha256=digest, packageIntegrity=locked['integrity'], patches=patches), ensure_ascii=False, indent=2) + '\n'
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--module-root', required=True, type=Path, help='Unmodified locked npm node_modules directory')
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--lock', type=Path)
    options = parser.parse_args()
    for asset, expected in generate(options.module_root, options.lock).items():
        destination = ROOT / 'app/src/main/assets' / asset
        if options.check:
            if not matches_text(destination, expected):
                raise ValueError('FILE_ACTIONS_RECIPE_STALE: ' + asset)
        else:
            write_text(destination, expected, encoding='utf8')
        print('PASS locked rc3 file actions recipe: ' + asset)


if __name__ == '__main__':
    main()
