/* DSHA_LAYOUT_RECOVERY_V1: preserve one presentation snapshot; never reset conversations or credentials. */
function dshaKeepLayout(sessionId, raw) {
  if (typeof raw !== "string" || raw.length > 2 * 1024 * 1024) throw new Error("LAYOUT_RECOVERY_SOURCE_LIMIT");
  const key = "dsha.layout-recovery.v1." + encodeURIComponent(sessionId) + "." + Date.now() + "." + Math.random().toString(36).slice(2);
  localStorage.setItem(key, raw);
  if (localStorage.getItem(key) !== raw) throw new Error("LAYOUT_RECOVERY_READBACK");
}
function dshaValidSurface(value) {
  try { const parsed = surface.parse(value); validateReferences(parsed); return true; } catch (_) { return false; }
}
class DshaLayoutBoundary extends react.Component {
  constructor(props) { super(props); this.state = { failed: false, message: "" }; }
  static getDerivedStateFromError() { return { failed: true }; }
  componentDidCatch() { console.error("DSHA_LAYOUT_RECOVERY_V1: sidebar presentation failed; conversation data retained"); }
  render() {
    if (!this.state.failed) return this.props.children;
    const english = window.__DSHA_LANGUAGE__ === "en";
    return react.createElement("aside", { role: "alert", "data-dsha-layout-recovery": "", style: {padding:12, maxWidth:"100%", overflow:"auto"} },
      react.createElement("p", null, this.state.message || (english ? "This session's panel could not be displayed. Your conversation and drafts were retained." : "本会话的面板无法显示，对话和草稿已保留。")),
      react.createElement("button", {type:"button", onClick:() => {
        try { this.props.actions.dshaRecoverLayout(this.props.sessionId); this.setState({failed:false,message:""}); }
        catch (_) { this.setState({message:english ? "The original layout could not be retained. Free some space and retry." : "原布局暂时无法保留，请释放空间后重试。"}); }
      }}, english ? "Restore this session's panel" : "恢复此会话面板"));
  }
}
