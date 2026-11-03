import assert from 'node:assert/strict';
import fs from 'node:fs';
import {browserFixture} from './rc1-browser-fixture.mjs';
const fixture=await browserFixture();
try {
  const page=fixture.page;
  await page.evaluate(()=>{window.__ModuleLoader__={load:({factory})=>factory(()=>({}))};});
  await page.addScriptTag({content:fs.readFileSync('app/src/main/assets/builtin-plugins/dsh-web-mobile/lib/client.js','utf8').replace('var __cache = {};','window.mobileRequire=__localRequire; var __cache = {};')});
  await page.evaluate(()=>{
    window.disposers=[]; mobileRequire('./effects/phone-chrome.js').installPhoneChrome({effect:run=>disposers.push(run())});
    const css=mobileRequire('./styles/misc.css.js'); const style=document.createElement('style'); style.textContent=css.MISC_CSS; document.head.append(style);
    const host=document.getElementById('root'); host.innerHTML='<div data-slot="conversation.input.dock"><div class="dsws-capsule" style="display:flex;width:290px;max-width:90vw;overflow:hidden;gap:6px;padding:3px 7px">'+Array.from({length:11},(_,i)=>'<span class="dsws-seg" style="flex:none;padding:2px 7px">'+(i===9?'<button class="dsws-skillbtn">⊞</button>':i===10?'<button class="dsws-fold-toggle">⌄</button>':'88')+'</span>').join('')+'</div></div>';
    window.writes=0; const root=document.documentElement, original=root.style.setProperty.bind(root.style); root.style.setProperty=(key,value,...rest)=>{if(key.startsWith('--dsha-mobile'))writes++;return original(key,value,...rest);};
  });
  assert.equal(await page.evaluate(()=>matchMedia('(pointer:coarse)').matches),true);
  for (const width of [320,393,800]) {
    await page.setViewportSize({width,height:500});
    await page.waitForFunction(()=>document.documentElement.style.getPropertyValue('--dsha-mobile-visible-vh')==='500px');
    const result=await page.locator('.dsws-capsule').evaluate(el=>{el.scrollLeft=el.scrollWidth;return {overflow:getComputedStyle(el).overflowX,padding:getComputedStyle(el).paddingRight,visible:el.querySelector('.dsws-fold-toggle').getBoundingClientRect().right<=el.getBoundingClientRect().right+1};});
    assert.equal(result.overflow,'auto'); assert.equal(result.padding,'4px'); assert.equal(result.visible,true);
  }
  await page.evaluate(()=>{writes=0;for(let i=0;i<200;i++)visualViewport.dispatchEvent(new Event('scroll'));});
  await page.waitForTimeout(200); assert.equal(await page.evaluate(()=>writes),0,'same viewport must not invalidate root style');
  await page.evaluate(()=>{Object.defineProperty(visualViewport,'height',{configurable:true,value:240});visualViewport.dispatchEvent(new Event('resize'));disposers.reverse().forEach(fn=>fn?.());});
  await page.waitForTimeout(200); assert.equal(await page.evaluate(()=>document.documentElement.style.getPropertyValue('--dsha-mobile-visible-vh')),'','dispose cancels late writes');
  await page.setViewportSize({width:1200,height:600});
  assert.equal(await page.locator('.dsws-capsule').evaluate(el=>getComputedStyle(el).paddingRight),'7px','desktop remains original');
  console.log(JSON.stringify({engine:process.env.DSHA_BROWSER||'chromium',capsuleWidths:[320,393,800],rootDedupe:true,dispose:true,desktop:true}));
} finally {await fixture.close();}
