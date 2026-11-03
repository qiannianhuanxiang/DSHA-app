import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { testRuntime } from './test-runtime-fixture.mjs';
import { classifyCommand } from '../app/src/main/assets/builtin-plugins/dsh-destructive-guard/lib/destructive.js';
const root=path.resolve(import.meta.dirname,'..'), assets=path.join(root,'app/src/main/assets');
const raw=testRuntime('raw'), directory=fs.mkdtempSync(path.join(root,'app/build/guard172-'));
const modules=path.join(directory,'node_modules');fs.mkdirSync(modules);
const patches=['guard-tools-patch.json','guard-bash-patch.json','guard-fs-patch.json','guard-editor-patch.json'].map(name=>JSON.parse(fs.readFileSync(path.join(assets,name),'utf8')));
const owned=new Set(patches.map(recipe=>recipe.module.split('/').slice(0,2).join('/')));
for(const name of fs.readdirSync(path.join(raw,'node_modules'))){
 if(name==='@deepseek-ai') {fs.mkdirSync(path.join(modules,name));for(const child of fs.readdirSync(path.join(raw,'node_modules',name)))if(!owned.has(name+'/'+child))fs.symlinkSync(path.join(raw,'node_modules',name,child),path.join(modules,name,child),process.platform==='win32'?'junction':'dir');}
 else if(fs.statSync(path.join(raw,'node_modules',name)).isDirectory())fs.symlinkSync(path.join(raw,'node_modules',name),path.join(modules,name),process.platform==='win32'?'junction':'dir');
}
for(const recipe of patches){
 const name=recipe.module.split('/').slice(0,2).join('/'), target=path.join(modules,recipe.module);fs.mkdirSync(path.dirname(target),{recursive:true});
 fs.copyFileSync(path.join(raw,'node_modules',name,'package.json'),path.join(modules,name,'package.json'));
 let source=fs.readFileSync(path.join(raw,'node_modules',recipe.module),'utf8');
 for(const patch of recipe.patches){assert.equal(source.split(patch.before).length-1,1,'locked rc2 anchor');source=source.replace(patch.before,patch.after);}fs.writeFileSync(target,source);
}
const policy=path.join(modules,'dsha-destructive-policy');fs.mkdirSync(policy);
for(const file of ['index.js','package.json','destructive.js','workdir.js'])fs.copyFileSync(path.join(assets,'destructive-policy',file),path.join(policy,file));
const {Context}=await import(pathToFileURL(path.join(modules,'@deepseek-ai/cordis/lib/index.js')));
const {ToolRuntime}=await import(pathToFileURL(path.join(modules,'@deepseek-ai/dsh-tools/lib/index.js')));
const {ApprovalService}=await import(pathToFileURL(path.join(modules,'@deepseek-ai/dsh-user-approval/lib/index.js')));
const {preparedEnvironment}=await import(pathToFileURL(path.join(policy,'index.js')));
const original=fs.readFileSync(path.join(root,'tools/test-destructive-guard.mjs'),'utf8');
const literal=original.slice(original.indexOf('const KNOWN_BYPASS = [')+'const KNOWN_BYPASS = '.length,original.indexOf('\n];',original.indexOf('const KNOWN_BYPASS = ['))+3);
const bypasses=Function('return '+literal)();assert.equal(bypasses.length,16);
let cases=0;
async function harness(command,outcome='allowed-once',mutate,policyName='ask'){
 const ctx=new Context();ctx.provide('systemPrompt',{tools(){},context(){},getContextOrder(){return 10;},section(){}});
 let collects=0;ctx.provide('shellEnv',{collect(){collects++;return {};}});
 const events=[{type:'turn/start',data:{}}],session={header:{cwd:'/root/work'},get seq(){return events.length;},eventAt(i){return events[i];},append(type,data){events.push({type,data});}};
 const agent={session,ctx};const tools=new ToolRuntime(ctx);
 if(outcome!=='missing-service')new ApprovalService(ctx,{policy:policyName});
 let asks=0,bodies=0;const definition={name:'bash',description:'Memory counter only; no shell is started.',parameters:{},output:{schema:{type:'object'},render:()=>[{type:'text',text:'counter'}]},execute(args,exec){preparedEnvironment(exec);bodies++;return {};}};
 const dispose=tools.register(definition);
 ctx.on('approval/request',async request=>{asks++;assert(request.displayReason.en.includes('DSHA'));if(mutate)await mutate({ctx,tools,agent,session,dispose,definition,request});return outcome;});
 const result=await ctx.tools.execute({name:'bash',callId:'case-'+cases,arguments:{command},agent,signal:new AbortController().signal});
 await ctx.fiber.dispose();cases++;return {asks,bodies,collects,result};
}
for(const [command] of bypasses){
 assert(classifyCommand(command,{cwd:'/root/work'}).requiresApproval,command);
 for(const outcome of ['allowed-once','rejected','cancelled','unavailable','missing-service']){
  const result=await harness(command,outcome);assert.equal(result.bodies,outcome==='allowed-once'?1:0,command+' '+outcome+' '+JSON.stringify(result.result));assert.equal(result.asks,outcome==='missing-service'?0:1);assert.equal(result.collects,1);
 }
}
for(const command of ['pwd','ls /tmp','cat /tmp/log','printf "rm -rf /"','sed -n "1p" /tmp/log','git status','curl https://example.com']){const result=await harness(command);assert.equal(result.asks,0,command);assert.equal(result.bodies,1);}
assert.equal((await harness('rm /tmp/fixture','allowed-once',undefined,'never')).bodies,0);
assert.equal((await harness('rm /tmp/fixture','allowed-once',({session})=>{session.header.cwd='/changed';})).bodies,0);
assert.equal((await harness('rm /tmp/fixture','allowed-once',({agent})=>{agent.session={header:{cwd:'/root/work'}};})).bodies,0);
assert.equal((await harness('rm /tmp/fixture','allowed-once',({tools,dispose,definition})=>{dispose();tools.register({...definition,execute(){throw Error('replacement must not run');}});})).bodies,0);
assert.equal((await harness('rm /tmp/fixture','allowed-once',({definition})=>{definition.execute=()=>{throw Error('mutated body must not run');};})).bodies,0);
assert.equal((await harness('rm /tmp/fixture','allowed-once',({agent})=>{agent.ctx=new Context();})).bodies,0);
assert.equal((await harness('rm /tmp/fixture','allowed-once',({session})=>{session.header={...session.header};})).bodies,0);
assert.equal((await harness('rm /tmp/fixture','allowed-once',({ctx})=>{ctx.set('approval',{request(){return 'allowed-once';},effectivePolicy(){return 'ask';}});})).bodies,0);
assert.equal((await harness('rm /tmp/fixture','allowed-once',({ctx})=>{ctx.set('shellEnv',{collect(){throw Error('replacement');}});})).bodies,0);
const oldEnv=process.env.DSHA_GUARD_TEMP;
assert.equal((await harness('rm /tmp/fixture','allowed-once',()=>{process.env.DSHA_GUARD_TEMP='changed';})).bodies,0);
if(oldEnv===undefined)delete process.env.DSHA_GUARD_TEMP;else process.env.DSHA_GUARD_TEMP=oldEnv;
for(const key of ['arguments','name']) {
 const ctx=new Context();ctx.provide('systemPrompt',{tools(){},context(){},getContextOrder(){return 10;}});ctx.provide('shellEnv',{collect(){return {};}});new ApprovalService(ctx,{policy:'ask'});const tools=new ToolRuntime(ctx);let bodies=0,asks=0;
 for(const name of ['bash','read'])tools.register({name,description:'counter',parameters:{},output:{schema:{type:'object'},render:()=>[]},execute(){bodies++;return {};}});
 const session={header:{cwd:'/root/work'},seq:1,eventAt(){return {type:'turn/start',data:{}}},append(){}};
 ctx.on('approval/request',async()=>{asks++;return 'allowed-once';});
 ctx.on('tools/execute',async(exec,next)=>{exec[key]=key==='name'?'bash':{command:'rm /unapproved'};return next();});
 const result=await tools.execute({name:key==='name'?'read':'bash',arguments:{command:'rm /approved'},callId:'mutation-'+key,agent:{ctx,session},signal:new AbortController().signal});assert(result.isError);assert.equal(bodies,0);assert.equal(asks,key==='name'?0:1);await ctx.fiber.dispose();cases++;
}
// Actual rc2 filesystem provider and actual write/edit/editor bodies, private temporary data only.
const {LocalFileSystem}=await import(pathToFileURL(path.join(modules,'@deepseek-ai/dsh-fs-local/lib/index.js')));
const fsTools=await import(pathToFileURL(path.join(modules,'@deepseek-ai/dsh-tool-fs/lib/index.js')));
const editor=await import(pathToFileURL(path.join(modules,'@deepseek-ai/dsh-tool-str-replace-editor/lib/index.js')));
for(const outcome of ['allowed-once','rejected','cancelled','unavailable']){
 const ctx=new Context();ctx.provide('systemPrompt',{tools(){},context(){},getContextOrder(){return 10;},getSectionOrder(){return 10;},section(){}});const tools=new ToolRuntime(ctx);new ApprovalService(ctx,{policy:'ask'});new LocalFileSystem(ctx,{cwd:directory,diffBasisMaxBytes:1048576});
 fsTools.apply(ctx,{readLimit:2000,readMaxLineLength:2000,readMaxBytes:1048576,readStreamMinSize:1048576});editor.apply(ctx,{maxOutputChars:16000,description:'temporary editor'});
 const session={header:{cwd:directory},seq:1,eventAt(){return {type:'turn/start',data:{}}},append(){}};let asks=0;ctx.on('approval/request',async()=>{asks++;return outcome;});
 const call=(name,args)=>tools.execute({name,arguments:args,callId:'fs-'+cases++,agent:{ctx,session},signal:new AbortController().signal});
 const existing=path.join(directory,'file-'+outcome);fs.writeFileSync(existing,'original');
 let result=await call('write',{file_path:existing,content:'replacement'});assert.equal(result.isError===true,outcome!=='allowed-once');assert.equal(fs.readFileSync(existing,'utf8'),outcome==='allowed-once'?'replacement':'original');assert.equal(asks,1);
 const fresh=path.join(directory,'fresh-'+outcome);result=await call('write',{file_path:fresh,content:'fresh'});assert.equal(result.isError===true,false);assert.equal(asks,1);
 result=await call('edit',{file_path:existing,old_string:outcome==='allowed-once'?'replacement':'original',new_string:'edited'});assert.equal(result.isError===true,outcome!=='allowed-once');assert.equal(asks,2);
 result=await call('str_replace_editor',{command:'insert',path:existing,insert_line:0,new_str:'prefix'});assert.equal(result.isError===true,outcome!=='allowed-once');assert.equal(asks,3);await ctx.fiber.dispose();
}
// The real bash body must consume exactly the collected environment. Executor is a memory seam.
const actualBash=await import(pathToFileURL(path.join(modules,'@deepseek-ai/dsh-tool-bash/lib/index.js')));
for(const outcome of ['allowed-once','rejected']){
 const ctx=new Context();ctx.provide('systemPrompt',{tools(){},context(){},getContextOrder(){return 10;},getSectionOrder(){return 10;},section(){}});new ToolRuntime(ctx);new ApprovalService(ctx,{policy:'ask'});
 let collects=0,bodies=0,asks=0;ctx.provide('shellEnv',{collect(){collects++;return {DSH_GUARD_FIXTURE:'frozen'};}});
 ctx.provide('shell',{resolve(request){assert.equal(request.command,'python -c "opaque()"');assert.equal(request.workdir,'/root/work');assert.deepEqual(request.dshEnv,{DSH_GUARD_FIXTURE:'frozen'});return request;},async execute(){bodies++;return {result:async()=>({exitCode:0,signal:null,timedOut:false,aborted:false,timeoutMs:30000,stdout:{text:'',truncated:false},stderr:{text:'',truncated:false}})}}});
 actualBash.apply(ctx,{enableRunInBackground:false,promoteOnTimeout:false});ctx.on('approval/request',async()=>{asks++;return outcome;});
 const session={header:{cwd:'/root/work'},seq:1,eventAt(){return {type:'turn/start',data:{}}},append(){}};
 const result=await ctx.tools.execute({name:'bash',arguments:{command:'python -c "opaque()"',description:'counter-only opaque fixture'},callId:'actual-bash-'+cases++,agent:{ctx,session},signal:new AbortController().signal});
 assert.equal(bodies,outcome==='allowed-once'?1:0,JSON.stringify(result));assert.equal(asks,1);assert.equal(collects,1);await ctx.fiber.dispose();
}
// Invoke the real run_code transport's bindings through its real scheduler; language runtime is a memory seam.
for(const outcome of ['allowed-once','rejected','cancelled','unavailable']){
 const ctx=new Context();ctx.provide('systemPrompt',{tools(){},context(){},getContextOrder(){return 10;},getSectionOrder(){return 10;},section(){}});ctx.provide('shellEnv',{collect(){return {};}});new ApprovalService(ctx,{policy:'ask'});new ToolRuntime(ctx,{mode:'ptc'});
 let bodies=0,asks=0;ctx.tools.register({name:'bash',description:'counter',parameters:{},output:{schema:{type:'object'},render:()=>[]},execute(args,exec){preparedEnvironment(exec);bodies++;return {};}});
 ctx.provide('ptcRuntime',{language:'typescript',resolve(request){return request;},async run(request){try{await request.bindings[0].functions.bash({command:'python -c "opaque()"'});return {logs:[],value:{}};}catch(error){return {logs:[],error:{kind:'exception',message:error.message}};}}});
 const events=[{type:'turn/start',data:{}}],session={header:{cwd:'/root/work'},get seq(){return events.length;},eventAt(i){return events[i]},append(type,data){events.push({type,data});}};
 ctx.on('approval/request',async request=>{assert(request.callId.endsWith(':ptc:1'));asks++;return outcome;});
 await ctx.tools.execute({name:'run_code',arguments:{code:'await tools.bash({command:"opaque"})',description:'memory runtime invoking real transport binding'},callId:'ptc-'+cases++,agent:{ctx,session},signal:new AbortController().signal});
 assert.equal(bodies,outcome==='allowed-once'?1:0);assert.equal(asks,1);await ctx.fiber.dispose();
}
// A file changed during approval must remain the other writer's bytes, not the approved replacement.
for(const name of ['write','edit','str_replace_editor']){
 const ctx=new Context();ctx.provide('systemPrompt',{tools(){},context(){},getContextOrder(){return 10;},getSectionOrder(){return 10;},section(){}});new ToolRuntime(ctx);new ApprovalService(ctx,{policy:'ask'});new LocalFileSystem(ctx,{cwd:directory,diffBasisMaxBytes:1048576});fsTools.apply(ctx,{readLimit:2000,readMaxLineLength:2000,readMaxBytes:1048576,readStreamMinSize:1048576});editor.apply(ctx,{});
 const file=path.join(directory,'race-'+name);fs.writeFileSync(file,'original');let asks=0;ctx.on('approval/request',async()=>{asks++;fs.writeFileSync(file,'concurrent-writer');return 'allowed-once';});
 const session={header:{cwd:directory},seq:1,eventAt(){return {type:'turn/start',data:{}}},append(){}};
 const args=name==='write'?{file_path:file,content:'replacement'}:name==='edit'?{file_path:file,old_string:'original',new_string:'replacement'}:{command:'insert',path:file,insert_line:0,new_str:'replacement'};
 const result=await ctx.tools.execute({name,arguments:args,callId:'race-'+cases++,agent:{ctx,session},signal:new AbortController().signal});assert(result.isError);assert.equal(asks,1);assert.equal(fs.readFileSync(file,'utf8'),'concurrent-writer');await ctx.fiber.dispose();
}
console.log(JSON.stringify({status:'PASS',cases,historicalBypasses:bypasses.length,mandatoryProfileIndependent:true,ordinaryReadonlyNoPrompt:true,realRc2:true,temporaryFilesystem:true,fixture:directory}));
