# DSHA 0.2.1-rc.3-bxroot.15-dsh0.2.1-rc.3 / build217

- Fixed the structural reason screen-control pauses could not be cleared: there were 11 pause reasons but most had no release path, and nothing recorded whether the condition behind a pause still held. Components now publish their condition (panel expanded, editor visible, voice recognizer active, overlay attached) and a pause is released only when that condition has actually ended; a missing answer counts as still paused.
- Fixed voice input: tapping the microphone paused screen control, but nothing cleared it after the voice UI exited, so only killing the process recovered. Releasing now happens when recognition ends.
- Fixed text input: opening the composer paused screen control and closing it left the pause in place. Closing the editor now releases it.
- Fixed the floating assistant panel: expanding and then collapsing no longer leaves an `ASSISTANT_PANEL_EXPANDED` pause behind.
- User pauses, stop-notification rules, policy and status failures still never auto-release.
- Otherwise identical to build214: bxroot runtime, Termux path compatibility, startup and extraction speedups, native call commands.
- Not verified on a device this round. If `SCREEN_CONTROL_PAUSED` still appears, `adb logcat -s DSHA` prints `checkBegin refused: paused reason=...` and names the cause.
