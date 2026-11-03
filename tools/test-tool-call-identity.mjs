import assert from 'node:assert/strict';
import { testRuntime } from './test-runtime-fixture.mjs';
import { readFile,writeFile,cp,mkdtemp,symlink,unlink,rm } from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {pathToFileURL} from 'node:url';
import path from 'node:path';
const root=path.resolve(import.meta.dirname,'..'), assets=path.join(root,'app/src/main/assets');
const raw=path.join(testRuntime('raw'),'node_modules');
const work=await mkdtemp(path.join(root,'app/build/tool66-'));
assert.ok(work.startsWith(path.join(root,'app/build')+path.sep));
const recipes=['tool-stream-identity','tool-block-identity','tool-dispatch-validation','tool-messages-history','tool-history-display'];
const registry=JSON.parse(await readFile(path.join(assets,'runtime-patches.json'),'utf8'));
for(const id of recipes) assert.equal(registry.active.filter(row=>row.asset===id+'-patch.json').length,1,'production active registration');
const managed=process.env.DSHA_REQUIRE_MANAGED_TOOL_IDENTITY==='1'?path.join(testRuntime('managed'),'node_modules'):undefined;
const outputs=new Map();
await symlink(raw,path.join(work,'node_modules'),process.platform==='win32'?'junction':'dir');
try {
  for(const id of recipes) {
    const recipe=JSON.parse(await readFile(path.join(assets,id+'-patch.json'),'utf8'));
    const file=path.join(raw,recipe.module), bytes=await readFile(file);
    assert.equal(createHash('sha256').update(bytes).digest('hex'),recipe.canonicalSourceSha256);
    const packageDir=path.dirname(path.dirname(recipe.module));
    const actualPackage=recipe.module.includes('/lib/types/')?path.dirname(packageDir):packageDir;
    const packageJson=JSON.parse(await readFile(path.join(raw,actualPackage,'package.json'),'utf8'));
    assert.equal(packageJson.version,recipe.packageVersion);
    const owned=path.join(work,path.basename(actualPackage));
    await cp(path.join(raw,actualPackage),owned,{recursive:true});
    let source=bytes.toString('utf8').replaceAll('\r\n','\n');
    if(id==='tool-messages-history') {
      const compat=JSON.parse(await readFile(path.join(assets,'deepseek-messages-compat-patch.json'),'utf8'));
      for(const patch of compat.patches) source=source.replace(patch.before,patch.prependAsset?await readFile(path.join(assets,patch.prependAsset),'utf8')+'\n'+patch.after:patch.after);
    }
    for(const patch of recipe.patches) {assert.equal(source.split(patch.before).length-1,1,id);source=source.replace(patch.before,patch.after);}
    if(managed) {
      const installed=await readFile(path.join(managed,recipe.module),'utf8');
      for(const patch of recipe.patches) assert.ok(installed.includes(patch.after),id+' must be present in the production-prepared managed fixture');
    }
    const out=path.join(owned,path.relative(actualPackage,recipe.module));
    const exports=id==='tool-stream-identity'?'toStreamChunks, toPiContext':id==='tool-dispatch-validation'?'executeToolCalls':id==='tool-messages-history'?'serialize':'';
    if(exports) source+='\nexport { '+exports+' };\n';
    await writeFile(out,source); outputs.set(id,out);
  }
  const load=id=>import(pathToFileURL(outputs.get(id)).href);
  const {BlockAssembler}=await load('tool-block-identity');
  const assembler=new BlockAssembler();
  assembler.push({type:'tool-call-delta',index:0,id:'',argumentsDelta:'{'});
  assembler.push({type:'tool-call-delta',index:0,id:'late-1',name:'bash',argumentsDelta:'}'});
  assembler.push({type:'tool-call-delta',index:0,id:'',argumentsDelta:''});
  assembler.push({type:'tool-call-delta',index:0,id:'',name:'   ',argumentsDelta:''});
  assembler.push({type:'block-end',index:0,block:{type:'tool-call',id:'',name:'',arguments:'{}'}});
  assert.deepEqual(assembler.blocks(),[{type:'tool-call',id:'late-1',name:'bash',arguments:'{}'}]);
  const incomplete=new BlockAssembler();incomplete.push({type:'tool-call-delta',index:0,id:'',argumentsDelta:'{}'});
  assert.equal(incomplete.blocks()[0].id,'','must not fabricate executable call identity');
  incomplete.push({type:'finish',reason:{kind:'max-tokens'}});assert.deepEqual(incomplete.blocks(),[]);
  const {toStreamChunks,toPiContext}=await load('tool-stream-identity');
  const events=[
    {type:'toolcall_start',contentIndex:0,partial:{content:[{type:'toolCall',id:'',name:''}]}},
    {type:'toolcall_delta',contentIndex:0,delta:'{}',partial:{content:[{type:'toolCall',id:'late-real',name:'read'}]}}
  ];
  const stream=toStreamChunks((async function*(){yield*events})(),10000); await stream.next();
  const late=(await stream.next()).value;assert.equal(late.id,'late-real');assert.equal(late.name,'read');await stream.return();
  const {executeToolCalls}=await load('tool-dispatch-validation');
  let dispatched=0;
  const ctx={agents:{requireInitiator:()=>({session:{append(){throw Error('must not write before preflight')}}})},tools:{prepare(){dispatched++}}};
  await assert.rejects(executeToolCalls(ctx,1,1,[{type:'tool-call',id:'valid',name:'bash',arguments:'{}'},{type:'tool-call',id:'',name:'',arguments:'{}'}],new AbortController().signal,()=>{}),/DSHA_INVALID_TOOL_CALL/);
  assert.equal(dispatched,0,'mixed invalid step must have zero side effects');
  const {serialize}=await load('tool-messages-history');
  const bad=[{role:'assistant',content:[{type:'tool-call',id:'old',name:'',arguments:'{}'}]}];
  const original=structuredClone(bad);
  assert.throws(()=>serialize({}, {}, bad,new Map(),()=>{}),/DSHA_INVALID_TOOL_HISTORY/);assert.deepEqual(bad,original);
  await assert.rejects(toPiContext({messages:bad},new Map()),/DSHA_INVALID_TOOL_HISTORY/);assert.deepEqual(bad,original);
  const history=[{role:'assistant',source:{kind:'model',provider:'deepseek',model:'test'},content:[{type:'tool-call',id:'real',name:'bash',arguments:'{}'}]}, {role:'tool',toolCallId:'real',isError:true,content:[{type:'text',text:'actual error'}]}];
  const request=serialize({model:'test',maxTokens:100},{models:[{id:'test',maxTokens:1000}],maxTokens:1000,defaults:{thinking:'disabled',reasoningEffort:'off'}},history,new Map(),()=>{});
  assert.equal(request.messages[0].content[0].id,'real');assert.equal(request.messages[0].content[0].name,'bash');
  assert.equal(request.messages[1].content[0].tool_use_id,'real');assert.equal(request.messages[1].content[0].is_error,true);
  assert.deepEqual(history[0].content,[{type:'tool-call',id:'real',name:'bash',arguments:'{}'}]);
  const pi=await toPiContext({messages:history,tools:[]},new Map());
  assert.equal(pi.messages[0].content[0].id,'real');assert.equal(pi.messages[0].content[0].name,'bash');
  assert.equal(pi.messages[1].toolCallId,'real');assert.equal(pi.messages[1].isError,true);
  console.log('tool66: canonical five-package anchors; late identity; empty/truncated blocks; zero mixed dispatch; read-only bad history; valid Messages structure PASS; managed-consumer verification='+Boolean(managed));
} finally { await unlink(path.join(work,'node_modules')); await rm(work,{recursive:true,force:true}); }
