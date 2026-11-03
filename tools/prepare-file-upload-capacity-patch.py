#!/usr/bin/env python3
"""Generate hash-locked decimal 5GB upload intake and browser transport recipes."""
import argparse
import hashlib
import json
from pathlib import Path
from source_text import write_text, matches_text

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
VERSION = '0.2.1-rc.3'
SERVER = '@deepseek-ai/dsh-client-file-upload/lib/index.js'
CLIENT = '@deepseek-ai/dsh-client-file-upload/lib/client.js'
CONVERSATION = '@deepseek-ai/dsh-client-ui-conversation/lib/client.js'
HASHES = {
    SERVER: '5dd5bd2d20656a1edeb1366e6b878afb04db519d2ba400135cb020ef037912fb',
    CLIENT: '4c09378ab7cb3dbe58253bd1fad8c4a00ebf5a93b462d96623edb7bd4e566305',
    CONVERSATION: 'b81765f0998430bbf5ab6389e89a082b799f017275c5abe1efb3c701f0fa708a',
}


def generate(modules):
    lock = json.loads((ROOT / 'tools/dsh-runtime/package-lock.json').read_text(encoding='utf8'))
    sources = {}
    for module, digest in HASHES.items():
        file = modules / module
        data = file.read_bytes()
        if hashlib.sha256(data).hexdigest() != digest:
            raise ValueError('UPLOAD_CAPACITY_OFFICIAL_SOURCE_CHANGED: ' + module)
        package = json.loads(file.parent.parent.joinpath('package.json').read_text(encoding='utf8'))
        if package['version'] != VERSION or lock['packages']['node_modules/' + package['name']]['version'] != VERSION:
            raise ValueError('UPLOAD_CAPACITY_PACKAGE_VERSION_CHANGED')
        sources[module] = data.decode('utf8')
    edits = {module: [] for module in HASHES}

    def patch(module, before, after):
        if sources[module].count(before) != 1 or before == after:
            raise ValueError('UPLOAD_CAPACITY_ANCHOR_CHANGED: ' + module)
        edits[module].append(dict(before=before, after=after))

    patch(SERVER, '//#region lib/types/http-route.js', '''// DSHA_UPLOAD_FILE_LIMIT_V1: decimal bytes, independent of native batch/session quotas.
const DSHA_UPLOAD_FILE_LIMIT = 5000000000;
function dshaUploadTooLarge() {
	return new RemoteError("session/attachment-invalid", "Single file exceeds 5 GB (5,000,000,000 bytes).", { reason: "FILE_TOO_LARGE", maxBytes: DSHA_UPLOAD_FILE_LIMIT });
}
async function* dshaUploadChunks(data, state) {
	let bytes = 0;
	for await (const chunk of data) {
		if (!(chunk instanceof Uint8Array)) throw new TypeError("file upload stream produced a non-Uint8Array chunk");
		if (chunk.byteLength > DSHA_UPLOAD_FILE_LIMIT - bytes) {
			state.exceeded = true;
			throw dshaUploadTooLarge();
		}
		bytes += chunk.byteLength;
		yield chunk;
	}
}
//#region lib/types/http-route.js''')
    patch(SERVER, '\tconst name = url.searchParams.get("name") ?? void 0;', '''	const name = url.searchParams.get("name") ?? void 0;
	const declared = request.headers.get("content-length");
	if (declared !== null && /^\\d+$/.test(declared) && BigInt(declared) > BigInt(DSHA_UPLOAD_FILE_LIMIT)) {
		await request.body?.cancel().catch(() => {});
		return new Response("Single file exceeds 5 GB (5,000,000,000 bytes).", { status: 413 });
	}''')
    patch(SERVER, '\tconst reader = body.getReader();\n\ttry {', '\tconst reader = body.getReader();\n\tlet complete = false;\n\ttry {')
    patch(SERVER, '\t\t\tif (chunk.done) return;', '\t\t\tif (chunk.done) { complete = true; return; }')
    patch(SERVER, '\t} finally {\n\t\treader.releaseLock();', '\t} finally {\n\t\tif (!complete) await reader.cancel().catch(() => {});\n\t\treader.releaseLock();')
    patch(SERVER, '\t\t\tsignal.throwIfAborted();\n\t\t\treturn this.commit(agent, async () => this.ctx.attachments.admitEncodedFile({', '''			signal.throwIfAborted();
			// Legacy RPC callers retain validation; browser files always use the binary route.
			const encoded = request.data;
			const padding = encoded.endsWith("==") ? 2 : encoded.endsWith("=") ? 1 : 0;
			if (Math.floor(encoded.length / 4) * 3 - padding > DSHA_UPLOAD_FILE_LIMIT) throw dshaUploadTooLarge();
			return this.commit(agent, async () => this.ctx.attachments.admitEncodedFile({''')
    start = sources[SERVER].index('\t\tasync uploadStream(request) {')
    end = sources[SERVER].index('\n\t\t/**', start)
    patch(SERVER, sources[SERVER][start:end], '''		async uploadStream(request) {
			const agent = await this.resolveAgent(request.sessionId);
			const state = { exceeded: false };
			try {
				return await this.commit(agent, async () => this.ctx.attachments.saveFileStream({
					data: dshaUploadChunks(request.data, state),
					...request.signal === void 0 ? {} : { signal: request.signal },
					...request.name === void 0 ? {} : { name: request.name }
				}));
			} catch (error) {
				// The attachment store cleans its partial temporary before wrapping intake errors.
				// Restore only our own overflow reason after that cleanup, never mint a receipt.
				if (state.exceeded) throw dshaUploadTooLarge();
				throw error;
			}
		}''')

    start = sources[CLIENT].index('\t\tfunction bytesToBase64(data) {')
    end = sources[CLIENT].index('\n\t\t//#endregion', start)
    patch(CLIENT, sources[CLIENT][start:end], '''		// DSHA_UPLOAD_BINARY_ONLY_V1: never aggregate browser files into a base64 RPC.
		const DSHA_UPLOAD_FILE_LIMIT = 5000000000;
		function dshaUploadTooLarge() {
			return new RemoteError("session/attachment-invalid", "Single file exceeds 5 GB (5,000,000,000 bytes).", { reason: "FILE_TOO_LARGE", maxBytes: DSHA_UPLOAD_FILE_LIMIT });
		}
		function dshaBoundedUploadStream(source) {
			const reader = source.getReader();
			let bytes = 0, released = false;
			const release = () => { if (!released) { released = true; reader.releaseLock(); } };
			return new ReadableStream({
				async pull(controller) {
					try {
						const item = await reader.read();
						if (item.done) { release(); controller.close(); return; }
						if (!(item.value instanceof Uint8Array)) throw new TypeError("file upload stream produced a non-Uint8Array chunk");
						if (item.value.byteLength > DSHA_UPLOAD_FILE_LIMIT - bytes) throw dshaUploadTooLarge();
						bytes += item.value.byteLength;
						controller.enqueue(item.value);
					} catch (error) {
						await reader.cancel(error).catch(() => {}); release(); controller.error(error);
					}
				},
				async cancel(reason) { try { await reader.cancel(reason); } finally { release(); } }
			});
		}''')
    start = sources[CLIENT].index('\t\t\tasync upload(sessionId, data, name, signal, onProgress) {')
    end = sources[CLIENT].index('\n\t\t};', start)
    patch(CLIENT, sources[CLIENT][start:end], '''			async upload(sessionId, data, name, signal, onProgress) {
				signal?.throwIfAborted();
				if (data instanceof Uint8Array) data = new Blob([data]);
				if (data instanceof Blob && data.size > DSHA_UPLOAD_FILE_LIMIT) return { ok: false, error: dshaUploadTooLarge() };
				if (data instanceof ReadableStream) data = dshaBoundedUploadStream(data);
				const query = new URLSearchParams({ sessionId });
				if (name !== void 0) query.set("name", name);
				const response = await this.post({
					path: `${FILE_UPLOAD_ROUTE}?${query.toString()}`,
					body: data,
					headers: { "content-type": "application/octet-stream" },
					...signal === void 0 ? {} : { signal },
					...onProgress === void 0 ? {} : { onProgress }
				});
				if (response.status !== 200) throw new Error(`file upload transport failed with HTTP ${String(response.status)}`);
				return parseFileUploadResult(response.body);
			}''')
    patch(CLIENT, '\t\t\t\t\tif (request.body instanceof ReadableStream) worker.postMessage(message, [request.body]);\n\t\t\t\t\telse worker.postMessage(message);', '''					try {
						if (request.body instanceof ReadableStream) worker.postMessage(message, [request.body]);
						else worker.postMessage(message);
					} catch (error) { finish(() => reject(error)); }''')
    patch(CONVERSATION, '\t\t\t\t\tif (isImageMediaType(file.type)) {', '''					// DSHA_LARGE_IMAGE_FILE_V1: retain the model's 20MiB/200MiB and pixel limits.
					// Larger images travel as their original file, without FileReader/base64 or image decode.
					if (isImageMediaType(file.type) && file.size <= 20 * 1024 * 1024) {''')

    results = {}
    for name, module in [('file-upload-capacity-patch.json', SERVER),
                         ('file-upload-binary-client-patch.json', CLIENT),
                         ('conversation-large-image-file-patch.json', CONVERSATION)]:
        recipe = dict(version=1, dshVersion=VERSION, module=module,
                      sourceSha256=HASHES[module],
                      packageIntegrity=lock['packages']['node_modules/' + module.split('/lib/')[0]]['integrity'],
                      purpose='Enforce 5,000,000,000 bytes per file, count streamed bytes before storage, retain image budgets and use bounded binary browser transport.',
                      patches=edits[module])
        results[name] = json.dumps(recipe, ensure_ascii=False, indent=2) + '\n'
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--modules', type=Path)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    modules = args.modules
    if modules is None:
        pointer = json.loads((ROOT / 'app/build/test-runtimes/current.json').read_text(encoding='utf8'))
        modules = Path(pointer['raw']) / 'node_modules'
    for name, expected in generate(modules).items():
        target = ASSETS / name
        if args.check:
            if not matches_text(target, expected):
                raise ValueError('UPLOAD_CAPACITY_PATCH_STALE: ' + name)
        else:
            write_text(target, expected, encoding='utf8')
    print('PASS locked upload capacity recipes ' + ('match generated source' if args.check else 'generated'))


if __name__ == '__main__':
    main()
