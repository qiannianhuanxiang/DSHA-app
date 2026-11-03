# DSHA 0.2.1-rc.3-bxroot.13-dsh0.2.1-rc.3 / build215

- Fixed screen control still returning `SCREEN_CONTROL_PAUSED` after Resume: resuming did not clear the assistant's expanded flag, and the assistant panel republishes its geometry on every layout change, which paused screen control again. Resuming now collapses the active assistant session first so both flags clear together; the deliberate pause on expand is unchanged.
- Retains build214's bxroot runtime, Termux path compatibility, startup and extraction speedups, native call commands, dsh-web-mobile 3.2.2, DSH rc.3, local and LAN access, and ten languages.
- This build contains only that fix and is not verified on a device: tap "Resume assistant screen control" in Settings → Device permissions → Screen control, then call `/app/ui/task-begin` to confirm `SCREEN_CONTROL_PAUSED` no longer appears.
