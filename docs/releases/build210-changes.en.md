# DSHA 0.2.1-rc.3-bxroot.8-dsh0.2.1-rc.3 / build210

- - Added Termux-like native calls (bxroot engine): Ubuntu can run Android native commands directly (`getprop`, `toybox`, `pm`, …; same-named guest programs win, or use `bx-host <command>` explicitly), and an Android native shell can return to the same Ubuntu session via `$BXROOT_ENTER`. Commands keep this app's UID with no privilege gain; which system commands work is decided by Android permissions.
- - Fixed 1×1 system glyph/badge windows (HyperDuo glyph, notes badge, etc.) on HyperOS-like systems triggering TARGET_WINDOW_OCCLUDED and making coordinate taps and swipes entirely unusable: windows of negligible area no longer participate in the occlusion decision (#212).
- - When stopping an app is blocked by "app list format could not be verified", the error now carries the first unexpected line (redacted, truncated) and names the user/system list, helping pinpoint ROM-specific pm output differences (#210 follow-up).
- - Retain dsh-web-mobile 3.2.2, DSH rc.3, local and LAN access, and ten languages. Phone acceptance remains paused.
