# DSHA 0.2.1-rc.3-20261028.0843-dsh0.2.1-rc.3 / build207

- Fixed the composer staying on the keyboard-height viewport after backgrounding with the keyboard up and returning: on refocus the real IME visibility is checked, and when the IME is gone the keyboard inset is released and one layout pass is forced (#190, native side).
- The regular inset path is unchanged while the IME is visible; the web-side viewport variable handling keeps the existing upstream implementation and is not modified this round.
- Retain dsh-web-mobile 3.2.2, DSH rc.3, local and LAN access, and ten languages. Phone acceptance remains paused.
