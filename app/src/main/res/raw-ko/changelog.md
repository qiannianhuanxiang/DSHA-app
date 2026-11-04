# DSHA 0.2.1-rc.3-bxroot.16-dsh0.2.1-rc.3 / build218

- 화면 조작 `begin`의 영구 실패(`ASSISTANT_EXPANDED` 교착)를 수정했습니다. 이제 어시스턴트 패널을 펼쳐도 화면 조작을 일시 중지하지 않습니다. 이 패널 창은 포커스를 받지 않고 입력기 대상도 되지 않으므로 이 방벽은 불필요했습니다. 오히려 교착을 일으켰습니다 — 펼치면 `expanded`가 켜져 화면 읽기가 `ASSISTANT_LOCAL_RESUME_REQUIRED`로 실패하고, 창이 제거되면 접을 수도 없어 일시 중지가 영원히 해제되지 않았습니다.
- 오버레이 지오메트리 게시 시 발생하던 같은 원인의 두 번째 일시 중지(`ASSISTANT_PANEL_EXPANDED`)와 창이 사라질 때 남던 펼침 플래그도 제거했습니다.
- 편집기와 음성은 여전히 각자 일시 중지합니다(`ASSISTANT_IME`, `ASSISTANT_VOICE`). 이 경로는 영향받지 않습니다.
- 이전 빌드에서 업그레이드해도 막히지 않습니다. `collapse()`가 기존 펼침 관련 일시 중지를 계속 해제합니다.
- 나머지는 build214와 같습니다: bxroot 런타임, Termux 경로 호환, 시작 및 압축 해제 가속, 네이티브 호출 명령.
- 이번에는 실기기에서 검증하지 않았습니다. `SCREEN_CONTROL_PAUSED`가 계속 나오면 `adb logcat -s DSHA`에 `checkBegin refused: paused reason=...`가 출력됩니다.
