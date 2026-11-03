# DSHA 0.2.1-rc.3-bxroot.14-dsh0.2.1-rc.3 / build216

- Fixed `begin` failing permanently for screen control: the stop-notification check compared a hard-coded notification id, but the notification is posted under a different id, so every call was treated as "stop notification missing" and paused screen control. Nothing cleared that pause once the assistant service exited, and restarting the app only helped briefly. The check now matches the stop action, and that exact pause reason is released when the service that required it is gone; user pauses and other reasons are untouched.
- Keeps the build215 fix: resuming screen control collapses the assistant session first, so the expanded flag cannot pause the just-resumed control again.
- Otherwise identical to build214: bxroot runtime, Termux path compatibility, startup and extraction speedups, native call commands.
- Not verified on a device this round: after installing, call `/app/ui/task-begin` and confirm `SCREEN_CONTROL_PAUSED` no longer appears.
