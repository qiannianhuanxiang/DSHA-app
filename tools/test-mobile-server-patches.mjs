import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { join } from 'node:path';
import { applyMobileServerPatch, upstreamServerHashes, mobileServerSourceMap } from './apply-mobile-client-patches.mjs';

for(const [name,hash] of Object.entries(upstreamServerHashes)) {
  test(`mobile server ${name} canonical output is idempotent and input drift is rejected`,()=>{
    const output=readFileSync(join('app/src/main/assets/builtin-plugins/dsh-web-mobile/lib',name));
    assert.deepEqual(applyMobileServerPatch(name,output),output);
    assert.deepEqual(applyMobileServerPatch(name,applyMobileServerPatch(name,output)),output);
    assert.throws(()=>applyMobileServerPatch(name,Buffer.concat([output,Buffer.from('\n// drift')])) ,/fixed commit\/anchor/);
    const upstream=join(process.env.DSHA_MOBILE_UPSTREAM_DIR??'tools/mobile-plugin-upstream/lib',name);
    assert.ok(existsSync(upstream), 'provide the locked prebuilt source directory');
    {
      const bytes=readFileSync(upstream);assert.equal(createHash('sha256').update(bytes).digest('hex'),hash);
      assert.deepEqual(applyMobileServerPatch(name,bytes),output);
    }
    const map=JSON.parse(mobileServerSourceMap(name,output));
    assert.equal(map.sourcesContent[0],output.toString('utf8'));
    assert.deepEqual(map,JSON.parse(readFileSync(join('app/src/main/assets/builtin-plugins/dsh-web-mobile/lib',name+'.map'),'utf8')));
  });
}
