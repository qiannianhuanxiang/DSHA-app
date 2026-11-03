# DSHA 0.2.1-rc.3-bxroot.7-dsh0.2.1-rc.3 / build209

- - Fixed 1×1 system glyph/badge windows (HyperDuo glyph, notes badge, etc.) on HyperOS-like systems triggering TARGET_WINDOW_OCCLUDED and making coordinate taps and swipes entirely unusable: windows of negligible area no longer participate in the occlusion decision (#212).
- - When stopping an app is blocked by "app list format could not be verified", the error now carries the first unexpected line (redacted, truncated) and names the user/system list, helping pinpoint ROM-specific pm output differences (#210 follow-up).
- - Retain dsh-web-mobile 3.2.2, DSH rc.3, local and LAN access, and ten languages. Phone acceptance remains paused.
