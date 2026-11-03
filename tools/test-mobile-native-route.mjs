// Actual locked rc2 navigation, production native-route bridge and current mobile drawer.
// Session catalogue/reference transport is synthetic; no Android or model request is implied.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {browserFixture} from './rc1-browser-fixture.mjs';

const runtime = process.env.DSHA_TEST_RUNTIME || JSON.parse(fs.readFileSync('app/build/test-runtimes/current.json', 'utf8')).raw;
const assets = 'app/src/main/assets/';
const inputs = [
  assets + 'app-integration/client.js', assets + 'web-integration/page.js',
  assets + 'builtin-plugins/dsh-web-mobile/lib/client.js',
  path.join(runtime, 'node_modules/@deepseek-ai/dsh-client-ui-workspace/lib/client.js'),
  'tools/rc1-browser-fixture.mjs', import.meta.filename,
];
const sourceHashes = Object.fromEntries(inputs.map(file => [file, createHash('sha256').update(fs.readFileSync(file)).digest('hex')]));
const fixture = await browserFixture(runtime);
try {
  const page = fixture.page;
  await fixture.load('dsh-client-ui-workspace', [], 'module.exports.nativeFixture={UiWorkspaceService};');
  let integration = fs.readFileSync(assets + 'app-integration/client.js', 'utf8');
  const boundary = "return {inject:['conversation'";
  assert.equal(integration.split(boundary).length, 2, 'integration export boundary remains exact');
  integration = integration.replace(boundary, "return {installNativeRoute,inject:['conversation'");
  await page.addScriptTag({content:integration});
  await page.addScriptTag({content:fs.readFileSync(assets + 'web-integration/page.js', 'utf8')});
  await page.evaluate(() => { window.__ModuleLoader__ = {load:({factory}) => factory(() => ({}))}; });
  const mobile = fs.readFileSync(assets + 'builtin-plugins/dsh-web-mobile/lib/client.js', 'utf8');
  assert.equal(mobile.split('var __cache = {};').length, 2);
  await page.addScriptTag({content:mobile.replace('var __cache = {};', 'window.mobileRequire=__localRequire; var __cache = {};')});
  await page.evaluate(() => {
    const store = auditModules['@deepseek-ai/dsh-client-store'].createSnapshotStore;
    const Service = auditExports['@deepseek-ai/dsh-client-ui-workspace'].nativeFixture.UiWorkspaceService;
    const frame = document.getElementById('root');
    frame.dataset.mobileNav = 'frame';
    frame.innerHTML = '<aside><div>Retained drawer</div></aside><main><textarea data-composer-input>Preserve this draft</textarea></main><div data-shell-overlay></div>';
    const list = store({phase:'ready',ids:[],byId:{},projectionsBySession:{}});
    const workspaces = {list:store({phase:'ready',state:'idle',items:[],archivedSessionIds:[],pinnedSessionIds:[],error:null})};
    const state = window.nativeRouteTest = {frame,list,workspaces,reports:[],retained:[],released:[],panels:[],events:[],closes:0,
      disposers:[],draft:document.querySelector('textarea'),serial:0,nonce:'a'.repeat(32)};
    window.DshaTheme = {postMessage:text => state.reports.push(JSON.parse(text))};
    const release = id => {
      state.released.push(id);
      const snapshot = list.getSnapshot(), row = snapshot.byId[id];
      if (row) list.set({...snapshot,byId:{...snapshot.byId,[id]:{...row,retainedBy:{mainView:0}}}});
    };
    const sessions = {list,subagentAddress:() => undefined,retain(id,options) {
      const snapshot = list.getSnapshot();
      if (!snapshot.byId[id] || options.source !== 'mainView') throw Error('Invalid synthetic retain');
      state.retained.push(id);
      list.set({...snapshot,byId:{...snapshot.byId,[id]:{...snapshot.byId[id],retainedBy:{mainView:1}}}});
      return {sessionId:id,release:() => release(id)};
    }};
    const workspace = Object.create(Service.prototype);
    workspace.sessions = sessions; workspace.lifetime = new AbortController(); workspace.selection = store({sessionId:'old'});
    workspace.ctx = {layout:{selectPanel:id => state.panels.push(id)}};
    state.workspace = workspace;
    state.disposers.push(auditExports['dsh-app-integration'].installNativeRoute({sessions,workspaces,uiWorkspace:workspace}));
    document.addEventListener('dsha-session-open', event => state.events.push(event.detail.id));
    mobileRequire('./effects/drawer-close.js').installOverlayInteractions({
      effect:run => state.disposers.push(run()),sessions,
      layout:{toggleSidebar() { state.closes++; frame.toggleAttribute('data-sidebar-collapsed'); }},
    });
    window.resetNativeRouteTest = (options={}) => {
      frame.removeAttribute('data-sidebar-collapsed');
      state.reports=[]; state.retained=[]; state.released=[]; state.panels=[]; state.events=[]; state.closes=0;
      const old = {id:'old',retainedBy:{mainView:1}}, target = {id:'target',retainedBy:{mainView:0}};
      list.set({phase:options.phase || 'ready',ids:options.fallback?['old']:['old','target'],
        byId:{old,target},projectionsBySession:{}});
      workspaces.list.set({phase:options.workspacePhase || 'ready',state:'idle',items:[],
        archivedSessionIds:options.archived?['target']:[],pinnedSessionIds:[],error:null});
      workspace.selection.set({sessionId:'old'});
      workspace.mainReference = {sessionId:'old',release:() => release('old')};
    };
    window.requestNativeRoute = (sessionId,nonce=state.nonce) => {
      const requestToken = 'bbbbbbbb-bbbb-bbbb-bbbb-' + String(++state.serial).padStart(12,'0');
      window.__dshaNativePresentation.route({nonce,generation:17,instance:'mobile-native-route',sessionId,requestToken});
      return requestToken;
    };
    window.__dshaNativePresentation.install({nonce:state.nonce,generation:17,instance:'mobile-native-route'});
    resetNativeRouteTest();
  });
  const settle = () => page.waitForTimeout(350);
  const snapshot = () => page.evaluate(() => ({
    retained:nativeRouteTest.retained,released:nativeRouteTest.released,panels:nativeRouteTest.panels,
    events:nativeRouteTest.events,closes:nativeRouteTest.closes,
    collapsed:nativeRouteTest.frame.hasAttribute('data-sidebar-collapsed'),
    selection:nativeRouteTest.workspace.selection.getSnapshot().sessionId,
    draft:document.querySelector('textarea').value,sameInput:nativeRouteTest.draft===document.querySelector('textarea'),
    routes:nativeRouteTest.reports.filter(report=>report.type==='session-route'),
  }));
  const noNavigation = value => {
    assert.deepEqual(value.retained,[]); assert.deepEqual(value.events,[]);
    assert.equal(value.selection,'old'); assert.equal(value.closes,0); assert.equal(value.collapsed,false);
    assert.equal(value.draft,'Preserve this draft'); assert.equal(value.sameInput,true);
  };
  const cases=[];
  for (const [name,options,id] of [
    ['unknown',{},'missing'], ['fallback outside Host ids',{fallback:true},'target'], ['archived',{archived:true},'target'],
  ]) {
    await page.evaluate(options=>resetNativeRouteTest(options),options);
    const token = await page.evaluate(id=>requestNativeRoute(id),id);
    await settle(); const value=await snapshot(); noNavigation(value);
    assert.equal(value.routes.find(route=>route.requestToken===token)?.status,'not-found');
    cases.push({name,pass:true});
  }
  await page.evaluate(() => {
    resetNativeRouteTest({phase:'pending'}); requestNativeRoute('target');
    nativeRouteTest.nonce='c'.repeat(32);
    __dshaNativePresentation.install({nonce:nativeRouteTest.nonce,generation:17,instance:'mobile-native-route'});
    nativeRouteTest.list.set({...nativeRouteTest.list.getSnapshot(),phase:'ready'});
  });
  await settle(); noNavigation(await snapshot()); cases.push({name:'old queued nonce cannot select after rebind',pass:true});
  await page.evaluate(() => {
    resetNativeRouteTest({phase:'pending'}); requestNativeRoute('target');
    __dshaNativePresentation.cancel();
    nativeRouteTest.list.set({...nativeRouteTest.list.getSnapshot(),phase:'ready'});
  });
  await settle(); noNavigation(await snapshot()); cases.push({name:'native hide cancellation prevents later catalogue opening',pass:true});
  await page.evaluate(() => { resetNativeRouteTest({workspacePhase:'pending'}); requestNativeRoute('target'); });
  await settle(); noNavigation(await snapshot());
  await page.evaluate(() => nativeRouteTest.workspaces.list.set({...nativeRouteTest.workspaces.list.getSnapshot(),phase:'ready'}));
  await settle();
  let value=await snapshot(); assert.deepEqual(value.retained,['target']); assert.deepEqual(value.events,['target']);
  assert.equal(value.selection,'target'); assert.equal(value.closes,1); assert.equal(value.collapsed,true);
  cases.push({name:'wait for real workspace archive baseline before opening',pass:true});
  await page.evaluate(() => resetNativeRouteTest());
  const token = await page.evaluate(() => requestNativeRoute('target'));
  await settle(); value=await snapshot();
  assert.deepEqual(value.retained,['target']); assert.deepEqual(value.released,['old']); assert.deepEqual(value.panels,[null]);
  assert.deepEqual(value.events,['target']); assert.equal(value.closes,1); assert.equal(value.collapsed,true);
  assert.equal(value.selection,'target'); assert.equal(value.routes.find(route=>route.requestToken===token)?.status,'opened');
  assert.equal(value.draft,'Preserve this draft'); assert.equal(value.sameInput,true);
  cases.push({name:'confirmed real rc2 selection closes drawer once and retains draft',pass:true});
  await page.evaluate(() => {
    nativeRouteTest.frame.removeAttribute('data-sidebar-collapsed');
    nativeRouteTest.retained=[]; nativeRouteTest.events=[]; nativeRouteTest.closes=0;
    requestNativeRoute('target');
  });
  await settle(); value=await snapshot();
  assert.deepEqual(value.retained,[],'already current must not retain a replacement');
  assert.deepEqual(value.events,['target']); assert.equal(value.closes,1); assert.equal(value.collapsed,true);
  cases.push({name:'already current confirmed selection closes drawer without replaying retain',pass:true});
  await page.evaluate(() => { resetNativeRouteTest(); requestNativeRoute('target','a'.repeat(32)); });
  await settle(); noNavigation(await snapshot()); cases.push({name:'stale incoming native nonce is rejected',pass:true});
  await page.evaluate(() => nativeRouteTest.disposers.reverse().forEach(dispose=>dispose?.()));
  assert.deepEqual(fixture.errors,[]);
  const finalHashes=Object.fromEntries(inputs.map(file=>[file,createHash('sha256').update(fs.readFileSync(file)).digest('hex')]));
  assert.deepEqual(finalHashes,sourceHashes,'source changed during regression; rerun the final bytes');
  const result={engine:process.env.DSHA_BROWSER||'chromium',passed:true,cases,sourceHashes,
    scope:'Real locked rc2 UiWorkspaceService.openSession/replaceMain and snapshot store; current mobile drawer close and production native route JS; synthetic catalogue/reference transport',
    deviceAcceptance:false,liveModelRequests:false};
  fs.mkdirSync('artifacts/pr172/mobile',{recursive:true});
  fs.writeFileSync(`artifacts/pr172/mobile/native-route-${result.engine}.json`,JSON.stringify(result,null,2)+'\n');
  console.log(JSON.stringify(result,null,2));
} finally { await fixture.close(); }
