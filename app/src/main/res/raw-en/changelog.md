# DSHA 0.2.1-rc.3-bxroot.16-dsh0.2.1-rc.3 / build218

- Fixed `begin` failing permanently for screen control (the `ASSISTANT_EXPANDED` deadlock): expanding the assistant panel no longer pauses screen control. That panel window is not focusable and never becomes the IME target, so the fence was unnecessary; worse, it deadlocked — expanding set `expanded`, which made screen reading fail with `ASSISTANT_LOCAL_RESUME_REQUIRED`, and once the window was removed the panel could not be collapsed, so the pause never cleared.
- Also removed the second pause from the same source raised when the overlay geometry is published (`ASSISTANT_PANEL_EXPANDED`), and the expansion flag left behind when the window is destroyed.
- The editor and voice paths still pause screen control on their own (`ASSISTANT_IME`, `ASSISTANT_VOICE`); those are unaffected.
- Upgrading from an older build cannot get stuck: `collapse()` still releases legacy expansion pauses.
- Otherwise identical to build214: bxroot runtime, Termux path compatibility, startup and extraction speedups, native call commands.
- Not verified on a device this round. If `SCREEN_CONTROL_PAUSED` still appears, `adb logcat -s DSHA` prints `checkBegin refused: paused reason=...`.
