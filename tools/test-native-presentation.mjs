import assert from 'node:assert/strict';
import {readFileSync,mkdirSync,writeFileSync} from 'node:fs';
import vm from 'node:vm';
import path from 'node:path';

const asset = 'app/src/main/assets/';
class DetailEvent extends Event { constructor(type,options={}) { super(type);this.detail=options.detail; } }
const doc = new EventTarget(), win = new EventTarget(), attrs = new Map();
doc.documentElement = {setAttribute:(key,value)=>attrs.set(key,value),getAttribute:key=>attrs.get(key)};
doc.body = {appendChild(){}}; doc.head={}; doc.hidden=false;
doc.querySelector = selector => selector === 'meta[name="theme-color"]' ? {content:'#123456'} : null;
doc.createElement = () => ({style:{},remove(){}});
win.top=win; const messages=[];win.DshaTheme={postMessage:text=>messages.push(JSON.parse(text))};
let raf, timerId=0; const timers=new Map(), observers=[];
const context=vm.createContext({window:win,document:doc,CustomEvent:DetailEvent,JSON,Promise,
  getComputedStyle:()=>({color:'rgb(18, 52, 86)',backgroundColor:'rgb(18, 52, 86)',getPropertyValue:()=>''}),
  MutationObserver:class { constructor(callback){this.callback=callback;observers.push(this);} observe(){} disconnect(){this.closed=true;} },
  requestAnimationFrame:callback=>{raf=callback;return 1;},cancelAnimationFrame:()=>{raf=undefined;},
  setTimeout:callback=>{timers.set(++timerId,callback);return timerId;},clearTimeout:id=>timers.delete(id)});
const page=readFileSync(asset+'web-integration/page.js','utf8');
vm.runInContext(page,context); const display=win.__dshaNativePresentation;
const nonce='1'.repeat(32); display.install({nonce,generation:7,instance:'web-instance'});raf();
assert.equal(messages.length,1);assert.deepEqual(messages[0],{type:'theme-color',nonce,generation:7,instance:'web-instance',sequence:0,color:'rgb(18, 52, 86)'});
vm.runInContext(page,context);assert.equal(win.__dshaNativePresentation,display,'Back-script reinjection must not duplicate native listeners');
display.install({nonce,generation:7,instance:'web-instance'});raf();assert.equal(messages.length,1,'unchanged theme is quiet');

const plugin=readFileSync(asset+'app-integration/client.js','utf8');
const start=plugin.indexOf('function installNativeRoute(ctx) {'),end=plugin.indexOf('function apply(ctx) {',start);
assert.ok(start>0&&end>start);vm.runInContext(plugin.slice(start,end),context);
let callback, workspaceCallback, opened=[], snapshot={phase:'ready',ids:['old','target','next','archived'],byId:{old:{id:'old',retainedBy:{mainView:1}},target:{id:'target',retainedBy:{mainView:0}},next:{id:'next',retainedBy:{mainView:0}},archived:{id:'archived',retainedBy:{}},fallback:{id:'fallback',retainedBy:{subagent:1}}}};
let workspaceSnapshot={phase:'ready',archivedSessionIds:['archived']};
let pendingReject;
const ctx={sessions:{list:{getSnapshot:()=>snapshot,subscribe:listener=>{callback=listener;return()=>{callback=undefined;};}}},
  workspaces:{list:{getSnapshot:()=>workspaceSnapshot,subscribe:listener=>{workspaceCallback=listener;return()=>{workspaceCallback=undefined;};}}},
  uiWorkspace:{openSession:id=>{opened.push(id);if(id==='target')return new Promise((resolve,reject)=>{pendingReject=reject;});}}};
const cleanup=context.installNativeRoute(ctx);
let serial=0;const request=sessionId=>({nonce,generation:7,instance:'web-instance',sessionId,requestToken:'aaaaaaaa-aaaa-aaaa-aaaa-'+String(++serial).padStart(12,'0')});
display.route({...request('target'),nonce:'2'.repeat(32)});assert.equal(opened.length,0,'wrong nonce cannot select a session');
display.route(request('missing'));assert.equal(opened.length,0);assert.equal(messages.at(-1).status,'not-found');
display.route(request('fallback'));assert.equal(opened.length,0);assert.equal(messages.at(-1).status,'not-found');
display.route(request('archived'));assert.equal(opened.length,0);assert.equal(messages.at(-1).status,'not-found');
snapshot.phase='pending';const target=request('target');display.route(target);assert.deepEqual(opened,[],'pending catalogue is not membership proof');
snapshot.phase='ready';callback();assert.deepEqual(opened,['target']);
assert.notEqual(messages.at(-1).requestToken,target.requestToken,'Host open return is not proof of actual selected session');
const next=request('next');display.route(next);assert.deepEqual(opened,['target','next']);
pendingReject(new Error('old request failed'));await Promise.resolve();await Promise.resolve();
assert.notEqual(messages.at(-1).requestToken,next.requestToken,'old request failure must not fail replacement');
snapshot.byId.old.retainedBy.mainView=0;snapshot.byId.next.retainedBy.mainView=1;callback();
assert.equal(messages.at(-1).requestToken,next.requestToken);assert.equal(messages.at(-1).status,'opened');
const count=messages.length;doc.dispatchEvent(new DetailEvent('dsha-native-session-result',{detail:JSON.stringify({nonce,requestToken:target.requestToken,sessionId:'target',status:'opened'})}));
assert.equal(messages.length,count,'stale acknowledgement is rejected');
workspaceSnapshot.phase='pending';display.route(request('target'));assert.deepEqual(opened,['target','next'],'pending archive catalog cannot authorize selection');
workspaceSnapshot.phase='ready';workspaceCallback();assert.deepEqual(opened,['target','next','target']);
snapshot.phase='pending';display.route(request('old'));display.cancel();snapshot.phase='ready';callback();assert.deepEqual(opened,['target','next','target'],'explicit hiding cancels pending route');
snapshot.phase='pending';display.route(request('old'));
const fresh='3'.repeat(32);display.install({nonce:fresh,generation:8,instance:'replacement'});raf();
snapshot.phase='ready';callback();assert.deepEqual(opened,['target','next','target'],'new page binding cancels prior pending Host route');
assert.equal(messages.at(-1).generation,8);assert.equal(messages.at(-1).nonce,fresh);
assert.equal(observers.filter(observer=>!observer.closed).length,1,'document rebind releases previous theme observer');
display.route({nonce:fresh,generation:8,instance:'replacement',sessionId:'old',requestToken:'dddddddd-dddd-dddd-dddd-dddddddddddd'});
snapshot.phase='pending';display.route({nonce:fresh,generation:8,instance:'replacement',sessionId:'target',requestToken:'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee'});
attrs.set('data-dsha-native-nonce','4'.repeat(32));snapshot.phase='ready';callback();assert.deepEqual(opened,['target','next','target','old'],'every deferred check must revalidate binding');
cleanup();assert.equal(callback,undefined);assert.equal(workspaceCallback,undefined);
display.route({nonce:fresh,generation:8,instance:'replacement',sessionId:'target',requestToken:'ffffffff-ffff-ffff-ffff-ffffffffffff'});assert.deepEqual(opened,['target','next','target','old'],'disposed Host listener cannot choose sessions');
const output=path.resolve(process.env.DSHA_NATIVE_PRESENTATION_OUTPUT||'app/build/native-presentation-js.json');
const buildRoot=path.resolve('app/build');
assert.ok(output.startsWith(buildRoot+path.sep),'Presentation test output must remain in app/build; historical evidence is immutable');
mkdirSync(path.dirname(output),{recursive:true});
writeFileSync(output,JSON.stringify({passed:17,scope:'Real production page/route JS; event and Host-service fixtures with rc2 ids and workspace archive contracts; no WebView/Gecko pixels or Android device'},null,2));
console.log('PASS_NATIVE_PRESENTATION_JS: theme provenance, duplicate injection, real Host route membership/current confirmation, stale responses and cleanup');
