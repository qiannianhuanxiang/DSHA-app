'use strict';
// MCP bridges Android screen actions and the native bridge's restricted image reader.
const fs=require('node:fs/promises');
const bridgePort=require('./bridge-port.cjs');
const MAX_SCREENSHOT_BYTES=16*1024*1024;
const MAX_FILE_IMAGE_BYTES=8*1024*1024;

const observationId={type:'string',minLength:1,maxLength:64};
const point={observationId,x:{type:'integer',minimum:0},y:{type:'integer',minimum:0}};
const definitions=[
  ['android_control','在用户明确要求操控实体手机时开始或结束当前对话的手机任务。begin绑定当前真实会话并显示原生执行状态；不新建聊天、不授予系统权限或自动同意确认。先begin，再启动应用或读屏操作；需要观察时状态窗自动隐藏，旧观察失效。任务完成用finish关闭本轮状态界面与控制权；完成后本轮不能重新获取控制，不能用于重试UNKNOWN动作。普通讨论、引用、翻译和未明确要求执行时不要调用。','/app/ui/task-begin',{action:{type:'string',enum:['begin','finish']}}],
  ['android_get_state','读取实体主屏（display 0）当前语义结构，返回treeId、nodeId和supportedActions；用android_node对新鲜节点执行语义动作，包括scroll_forward/scroll_backward。此树不签发坐标触控凭据，零面积bounds不能当触点。操作后再次验证；虚拟屏用 android_vscreen_*。','/app/ui/dump',{}],
  ['android_node','对实体主屏（display 0）最近android_get_state返回的treeId/nodeId执行supportedActions中的语义动作；scroll_forward/scroll_backward调用真实无障碍节点滚动，不需要截图坐标。节点凭据限当前任务/窗口/代次、30秒、一次使用。动作后重新读屏验证，Android接受不代表界面已改变；失败或UNKNOWN不得自动转坐标手势或重放。','/app/ui/node',{treeId:{type:'string',minLength:1,maxLength:64},nodeId:{type:'string',minLength:1,maxLength:64},action:{type:'string',enum:['click','long_click','scroll_forward','scroll_backward','focus']}}],
  ['android_screenshot','截取实体主屏（display 0），返回图片和JSON观察metadata。仅coordinatesAvailable:true且含observationId才可显式坐标触控；凭据限最新帧、30秒内、一次使用。需要系统截屏能力和DSHA授权。','/app/ui/screenshot',{}],
  ['android_read_image','读取现有的容器或当前用户共享存储静态 PNG、JPEG 或 WebP 图片，返回图片本身，文件上限 8 MiB。动图和 GIF 不支持；遵循桥路径、凭据和系统权限限制；不产生截图观察凭据，也不能授权坐标触控。','/app/readfile',{path:{type:'string',minLength:1,maxLength:4096}}],
  ['android_click','真实触控点击实体主屏（display 0）。observationId与坐标必须来自最近一次android_screenshot，不能用dump/tree替代；完成后观察验证效果。','/app/ui/tap',point],
  ['android_double_click','真实触控双击实体主屏同一位置（一次原子手势，不是失败重试）。使用最新截图observationId；完成后观察验证效果。','/app/ui/tap',point],
  ['android_long_press','真实触控长按实体主屏。使用最新截图observationId，ms限定500至5000；完成后观察验证效果。','/app/ui/tap',{...point,ms:{type:'integer',minimum:500,maximum:5000}}],
  ['android_type','向实体主屏（display 0）已聚焦输入框写入文字。涉及敏感操作时先取得用户确认。','/app/ui/input',{text:{type:'string',maxLength:2000}}],
  ['android_key','向实体主屏（display 0）发送 Android 导航键。','/app/ui/key',{name:{type:'string',enum:['back','home','recents','notifications','quicksettings']}}],
  ['android_swipe','真实触控滑动实体主屏。observationId与整条路径必须来自最新截图可用区域，不能用dump/tree替代；完成后观察验证效果。','/app/ui/swipe',{observationId,x1:{type:'integer',minimum:0},y1:{type:'integer',minimum:0},x2:{type:'integer',minimum:0},y2:{type:'integer',minimum:0},ms:{type:'integer',minimum:50,maximum:3000}}]
];
const tools=definitions.map(([name,description,,properties])=>({name,description,inputSchema:{type:'object',properties,required:Object.keys(properties),additionalProperties:false}}));
function validate(def,args){if(!args||typeof args!=='object'||Array.isArray(args))throw Error('INVALID_ARGUMENTS');const fields=def[3];if(Object.keys(args).some(k=>!Object.hasOwn(fields,k)))throw Error('UNKNOWN_ARGUMENT');for(const [key,rule]of Object.entries(fields)){const v=args[key];if(rule.type==='integer'&&(!Number.isInteger(v)||v<rule.minimum||rule.maximum!==undefined&&v>rule.maximum))throw Error('INVALID_'+key);if(rule.type==='string'&&(typeof v!=='string'||rule.minLength!==undefined&&v.length<rule.minLength||rule.maxLength&&v.length>rule.maxLength||rule.enum&&!rule.enum.includes(v)))throw Error('INVALID_'+key);}}
// 只识别完整原生错误；读屏正文可能包含终端日志里的 [ERR] 等普通文字。
function bridgeFailed(result){
  if(/^\s*(?:\[ERR\]|\[?(?:POLICY_BLOCKED|EXECUTION_UNKNOWN|NO_PERMISSION|DISABLED|UNAUTHORIZED|STOPPED)\b)/.test(result))return true;
  // ScreenReply carries execution facts as JSON. Preserve it verbatim: UNKNOWN
  // cannot become a successful tool result or permission to repeat the action.
  try{const value=JSON.parse(result);return value!==null&&typeof value==='object'&&!Array.isArray(value)
    &&value.ok===false&&['NOT_SENT','REJECTED','PARTIAL','UNKNOWN'].includes(value.state)
    &&typeof value.error==='string'&&value.error===value.code;}catch{return false;}
}
// Native capture returns the freshly generated PNG directly: the guest does not
// necessarily mount the current Android user's /storage/emulated/<id> directory.
function screenshotContent(result){let shot;try{shot=JSON.parse(result);}catch{throw Error('SCREENSHOT_RESPONSE_INVALID');}
  if(shot?.kind!=='dsha-screenshot-v1'||shot.mimeType!=='image/png'||typeof shot.data!=='string')throw Error('SCREENSHOT_RESPONSE_INVALID');
  if(shot.data.length>Math.ceil(MAX_SCREENSHOT_BYTES/3)*4)throw Error('SCREENSHOT_TOO_LARGE');
  const png=Buffer.from(shot.data,'base64');
  if(png.toString('base64')!==shot.data)throw Error('SCREENSHOT_RESPONSE_INVALID');
  if(png.length>MAX_SCREENSHOT_BYTES)throw Error('SCREENSHOT_TOO_LARGE');
  if(png.length<8||!png.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])))throw Error('SCREENSHOT_RESPONSE_INVALID');
  // Preserve the native observation credential and its restrictions beside the image.
  // Do not invent an id, keep hidden latest-frame state, or auto-capture/replay input.
  const {data,...metadata}=shot;
  return [{type:'text',text:JSON.stringify(metadata)},{type:'image',mimeType:'image/png',data:shot.data}];
}
function fileImageContent(result){let value;try{value=JSON.parse(result);}catch{throw Error('IMAGE_RESPONSE_INVALID');}
  if(value?.kind!=='dsha-file-image-v1'||typeof value.data!=='string'
    ||!['image/png','image/jpeg','image/webp'].includes(value.mimeType)
    ||value.data.length>Math.ceil(MAX_FILE_IMAGE_BYTES/3)*4)throw Error('IMAGE_RESPONSE_INVALID');
  const bytes=Buffer.from(value.data,'base64');
  if(bytes.length>MAX_FILE_IMAGE_BYTES||bytes.toString('base64')!==value.data)throw Error('IMAGE_RESPONSE_INVALID');
  const png=bytes.length>=8&&bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10]));
  const jpeg=bytes.length>=3&&bytes[0]===255&&bytes[1]===216&&bytes[2]===255;
  const webp=bytes.length>=12&&bytes.subarray(0,4).toString('latin1')==='RIFF'&&bytes.subarray(8,12).toString('latin1')==='WEBP';
  if(!({'image/png':png,'image/jpeg':jpeg,'image/webp':webp})[value.mimeType])throw Error('IMAGE_RESPONSE_INVALID');
  // A stored image is not a fresh physical-screen observation and carries no observation ID.
  return [{type:'text',text:JSON.stringify({kind:'dsha-file-image-v1',mimeType:value.mimeType})},
    {type:'image',mimeType:value.mimeType,data:value.data}];
}
function validateTask(task){
  if(!task||typeof task!=='object'||Array.isArray(task)
    ||typeof task.sessionId!=='string'||!task.sessionId.trim()||task.sessionId.length>256||task.sessionId.includes('\0')
    ||typeof task.turnId!=='string'||!/^[1-9][0-9]{0,15}$/.test(task.turnId)||!Number.isSafeInteger(Number(task.turnId)))
    throw Error('SCREEN_TASK_IDENTITY_REQUIRED');
  if(task.completedTurnId!==undefined&&(typeof task.completedTurnId!=='string'
    ||!/^[1-9][0-9]{0,15}$/.test(task.completedTurnId)||!Number.isSafeInteger(Number(task.completedTurnId))
    ||Number(task.completedTurnId)>=Number(task.turnId)))throw Error('SCREEN_TURN_INVALID');
  return task;
}
async function call(name,args,signal,dependencies={fs,fetch:globalThis.fetch},task){const def=definitions.find(t=>t[0]===name);if(!def)throw Error('UNKNOWN_TOOL');validate(def,args);const fileImage=name==='android_read_image';if(fileImage&&args.path.includes('\0'))throw Error('INVALID_path');if(!fileImage)validateTask(task);
  const token=(await dependencies.fs.readFile('/root/.dsh/.bridge_token','utf8')).trim();if(!token)throw Error('BRIDGE_NOT_READY');
  const route=name==='android_control'&&args.action==='finish'?'/app/ui/task-end':def[2];
  const url=new URL('http://127.0.0.1:'+bridgePort()+route);for(const [k,v]of Object.entries(args)){if(name==='android_control'&&k==='action')continue;url.searchParams.set(k,String(v));}
  if(!fileImage){url.searchParams.set('taskSessionId',task.sessionId);url.searchParams.set('turnId',task.turnId);
  if(task.completedTurnId!==undefined)url.searchParams.set('completedTurnId',task.completedTurnId);}
  if(name==='android_screenshot')url.searchParams.set('format','mcp');
  if(fileImage)url.searchParams.set('format','image');
  if(name==='android_double_click')url.searchParams.set('gesture','double_tap');
  if(name==='android_long_press')url.searchParams.set('gesture','long_press');
  const response=await dependencies.fetch(url,{signal,headers:{'X-Token':token}});if(!response.ok)throw Error('BRIDGE_HTTP_'+response.status);
  const raw=await readBridgeResponse(response,name==='android_screenshot'?24*1024*1024:fileImage?12*1024*1024:2*1024*1024);let result=raw;try{const parsed=JSON.parse(raw);result=typeof parsed.result==='string'?parsed.result:raw;}catch{}
  const failed=bridgeFailed(result)||(fileImage&&/^(?:FORBIDDEN|NOT_FOUND_OR_NO_PERMISSION|NOT_IMAGE|INVALID_IMAGE|ANIMATED_OR_INVALID_IMAGE|IMAGE_TOO_LARGE|IMAGE_BUSY|BAD_FORMAT|ERROR):?\b/.test(result));
  const content=name==='android_screenshot'&&!failed?screenshotContent(result):fileImage&&!failed?fileImageContent(result):[{type:'text',text:result}];
  return {content,isError:failed};
}
async function readBridgeResponse(response,limit){
  const advertised=Number(response.headers.get('content-length'));
  if(Number.isFinite(advertised)&&advertised>limit){await response.body?.cancel();throw Error('BRIDGE_RESPONSE_TOO_LARGE');}
  if(!response.body) return '';
  const reader=response.body.getReader(),parts=[];let size=0;
  try{for(;;){const {done,value}=await reader.read();if(done)break;size+=value.byteLength;if(size>limit){await reader.cancel();throw Error('BRIDGE_RESPONSE_TOO_LARGE');}parts.push(value);}}
  finally{reader.releaseLock();}
  return Buffer.concat(parts,size).toString('utf8');
}
function startServer(dependencies){
const active=new Map(),cancelled=new Set();let buffer='',queue=Promise.resolve(),ended=false,pending=0;
const send=message=>process.stdout.write(JSON.stringify({jsonrpc:'2.0',...message})+'\n');
async function receive(message){const {id,method,params={}}=message;if(method==='notifications/cancelled'){if(active.has(params.requestId))active.get(params.requestId).abort();else if(cancelled.size<128)cancelled.add(params.requestId);return;}if(id===undefined)return;if(ended||cancelled.delete(id)){send({id,error:{code:-32800,message:'Request cancelled before execution'}});return;}
  try{let result;
    if(method==='initialize')result={protocolVersion:params.protocolVersion||'2025-03-26',capabilities:{tools:{listChanged:false}},serverInfo:{name:'DSHA Android Computer Use',version:'0.1.0'},instructions:'Only deliberate user requests to act on the physical phone authorize a task; ordinary discussion, quotations or translation are not execution intent. Call android_control with action begin before any phone action, including an app launch. It binds this exact current conversation and turn automatically; do not ask the user to open a workspace assistant or create another chat. After a successful begin, call android_control with action finish once in final cleanup when the task is completed or abandoned. Finish closes this turn of phone control without cancelling the model conversation; do not begin again in the same turn after finishing. The native task window is hidden before screen observation and remains hidden until task completion or user pause, so it does not obstruct the target. If overlay visibility changes before an input, obtain a fresh observation and explicitly decide again; never silently retry. Necessary Android permissions and native confirmations remain required. These tools and /app/ui/* target only the physical main display (display 0), including while an independent virtual display exists. Use android_get_state to obtain fresh treeId/nodeId and supportedActions, then android_node for semantic click, long_click, focus, scroll_forward or scroll_backward; semantic scrolling calls the actual Android accessibility node action and requires no screenshot coordinates. Node credentials are single-use and bound to the observed task, target and epoch. Observe again after each action. Never automatically replace a refused or UNKNOWN node action with a coordinate swipe/click or replay it. Coordinate touch requires the observationId returned beside the latest android_screenshot image with coordinatesAvailable:true, within 30 seconds and for one gesture only; dump/tree is semantic evidence and cannot authorize coordinates. Pixel, target, epoch, occlusion and boundary checks remain mandatory. An accessibility action accepted by Android is not proof of a WebView DOM change: effectVerified:false requires a new observation. Choose any subsequent screenshot-based touch explicitly; never silently fall back or automatically replay. Tap, double click, long press and single-finger swipe are supported; multi-finger gestures are not exposed. Verify effects after actions. Task session/turn identities come from the actual execution transport, never model arguments or a global active-task fallback. For a user-requested virtual display, use mcp__dsha-vscreen__* tools: read android_vscreen_status first, reuse its coreInstance for explicit see/tree observations, and do not recreate an existing or pending Core. Use virtual tree/node for observed controls and editor for its focused input, keeping the same coreInstance/generation and observation identities. Never use a physical-screen action as fallback for a virtual-screen failure or silently switch displays. Respect native permission and confirmation results. Never replay an action whose outcome is unknown.'};
    else if(method==='ping')result={};
    else if(method==='tools/list')result={tools};
    else if(method==='tools/call'){const controller=new AbortController();active.set(id,controller);try{result=await call(params.name,params.arguments||{},controller.signal,dependencies,params._meta?.['dsha/phone-task']);}finally{active.delete(id);}}
    else{send({id,error:{code:-32601,message:'Method not found'}});return;}
    send({id,result});
  }catch(error){send({id,result:{content:[{type:'text',text:'Android operation unavailable: '+String(error.code||(/^[A-Z][A-Za-z0-9_]{0,60}$/.test(error.message)?error.message:'OPERATION_UNAVAILABLE'))+'. Review the native error or arguments. Coordinate input requires a fresh android_screenshot observationId; dump is not sufficient. If execution may have started, observe the screen and never automatically replay.'}],isError:true}});}
}
process.stdin.setEncoding('utf8');process.stdin.on('data',chunk=>{buffer+=chunk;if(buffer.length>1024*1024){process.stderr.write('MCP_INPUT_LIMIT\n');process.exit(1);}let end;while((end=buffer.indexOf('\n'))>=0){const line=buffer.slice(0,end);buffer=buffer.slice(end+1);if(!line.trim())continue;let value;try{value=JSON.parse(line);}catch{send({id:null,error:{code:-32700,message:'Parse error'}});continue;}if(value.method==='notifications/cancelled')void receive(value);else if(pending>=32)send({id:value.id??null,error:{code:-32000,message:'Request queue full'}});else{pending++;queue=queue.then(()=>receive(value)).finally(()=>pending--);}}});
process.stdin.on('end',()=>{ended=true;for(const controller of active.values())controller.abort();});
process.stdout.on('error',error=>{if(error.code==='EPIPE')process.exit(0);});
}
if(require.main===module)startServer();
module.exports={validate,validateTask,definitions,tools,bridgeFailed,screenshotContent,fileImageContent,readBridgeResponse,call,startServer};
