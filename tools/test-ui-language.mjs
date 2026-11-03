import { testRuntime } from './test-runtime-fixture.mjs';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';
import assert from 'node:assert/strict';
const assets='app/src/main/assets/',spec=JSON.parse(readFileSync(assets+'language-patch.json','utf8'));
const runtime = testRuntime('raw');
let source=readFileSync(runtime+'/node_modules/'+spec.module,'utf8');
for(const patch of spec.patches){const after=(patch.prependAsset?readFileSync(assets+patch.prependAsset,'utf8')+'\n':'')+patch.after;
 assert.equal(source.split(patch.before).length-1,1);source=source.replace(patch.before,()=>after);
}
new vm.Script(source);
for(const patch of spec.patches){const after=(patch.prependAsset?readFileSync(assets+patch.prependAsset,'utf8')+'\n':'')+patch.after;assert.equal(source.split(after).length-1,1);}
const helper=readFileSync(assets+'web-integration/language.js','utf8');
const tags=['zh','zh-Hant','en','ru','ja','ko','ar','pt','la','fr'];
const messages=JSON.parse(readFileSync('tools/i18n/web-messages.json','utf8')).messages;
const dictionaries=Object.fromEntries(tags.map(tag=>[tag,tag==='zh'||tag==='en'
 ?Object.fromEntries(messages.map(row=>[row.id,row[tag]]))
 :JSON.parse(readFileSync('tools/i18n/web-translations/'+tag+'.json','utf8'))]));

async function fixture(language='en'){
 let definition,service;const events=new Map(),selections=[],disposers=[];
 const window={__DSHA_LANGUAGE__:language,
  addEventListener:(type,fn)=>events.set(type,fn),removeEventListener:(type,fn)=>{if(events.get(type)===fn)events.delete(type);},
  dispatchEvent(event){if(event.type==='dsha-language-selected')selections.push(event.detail);events.get(event.type)?.(event);},
  __ModuleLoader__:{load(value){definition=value;}}};window.top=window;
 const document={documentElement:{lang:'en',dir:'ltr'},body:{textContent:'User content, 用户原文, نص المستخدم'},querySelector(){return {};}};
 const context=vm.createContext({window,document,navigator:{languages:['en'],language:'en'},console,
  CustomEvent:class {constructor(type,options){this.type=type;this.detail=options?.detail;}}});
 vm.runInContext(source,context);
 const exported=definition.factory(name=>name==='@deepseek-ai/dsh-client-store'?{defineStore:value=>value}:{});
 await exported.apply({fiber:{uid:1},configForms:{get(){return undefined;}},provide(name,value){assert.equal(name,'locale');service=value;},
  emit(){},effect(factory){disposers.push(factory());},slots:{installLocale(){},inject(){},register(){}}});
 // Register the extracted original dictionaries as their feature fibers do.
 const groups=new Map();for(const row of messages){if(!groups.has(row.namespace))groups.set(row.namespace,{zh:{},en:{}});
  groups.get(row.namespace).zh[row.key]=row.zh;groups.get(row.namespace).en[row.key]=row.en;}
 for(const [namespace,data] of groups)if(namespace!=='common'&&namespace!=='settings.locale')disposers.push(service.register(namespace,data));
 return {window,document,service,events,selections,dispose(){for(const fn of disposers.reverse())if(typeof fn==='function')fn();}};
}

test('real locked rc3 locale: every fixed UI key resolves in all ten languages; user content stays verbatim',async()=>{
 const page=await fixture('ar');
 assert.deepEqual([...page.service.getSnapshot().locales].map(row=>row.id).sort(),[...tags].sort());
 for(const tag of tags){
  page.window.__DSHA_LANGUAGE__=tag;page.window.dispatchEvent({type:'dsha-language'});
  assert.equal(page.service.getSnapshot().active,tag);
  assert.equal(page.document.documentElement.lang,tag);
  assert.equal(page.document.documentElement.dir,tag==='ar'?'rtl':'ltr');
  for(const row of messages)assert.equal(page.service.bind(row.namespace)(row.key),dictionaries[tag][row.id],tag+':'+row.id);
 }
 assert.equal(page.document.body.textContent,'User content, 用户原文, نص المستخدم');
 assert.deepEqual(page.selections,[],'native delivery is not an explicit user selection');
 page.dispose();assert.equal(page.events.size,0);assert.equal(page.service.catalog.size,2);
});

test('real locked rc3 native/web selection is bidirectional; Arabic direction clears; stale Host cannot override',async()=>{
 const page=await fixture('en');
 for(const tag of ['ar','zh-Hant','ja','en']){
  page.service.setLocale(tag);assert.equal(page.window.__DSHA_LANGUAGE__,tag);
  assert.equal(page.selections.at(-1),tag);assert.equal(page.service.getSnapshot().active,tag);
  assert.equal(page.document.documentElement.dir,tag==='ar'?'rtl':'ltr');
  page.service.adopt({getSnapshot:()=>({value:{preference:'zh'}})});
  assert.equal(page.service.getSnapshot().active,tag);
 }
 const before=page.service.getSnapshot().active;
 for(const invalid of ['system','ar-EG','zh-TW','invalid','<script>']){
  page.window.__DSHA_LANGUAGE__=invalid;page.window.dispatchEvent({type:'dsha-language'});
  assert.equal(page.service.getSnapshot().active,before);
 }
 assert.throws(()=>page.service.setLocale('unregistered'),/not registered/);
 page.dispose();
});

test('non-browser import keeps upstream available',()=>{
 const context=vm.createContext({});vm.runInContext(helper,context);
 assert.equal(typeof context.installDshaLanguageBridge({}),'function');
});
