# DSHA 0.2.1-rc.3-bxroot.9-dsh0.2.1-rc.3 / build211

- 네이티브 기능 명령 추가: Ubuntu에서 Termux:API 스타일의 `termux-clipboard-get/set`, `termux-notification`, `termux-toast`, `termux-vibrate`, `termux-torch`, `termux-open-url`, `termux-share`, `termux-location`, `termux-sensor`, `termux-battery-status`, `termux-dialog`를 바로 쓸 수 있습니다. `dsha-am start/broadcast`는 DSHA 자체 신원으로 화면을 열거나 브로드캐스트를 보냅니다(root 불필요, 화면 확인 정책에 따라 확인). `dsha-su`는 설정 → 기기 권한에서 「제한 없는 기기 셸」을 켜면 승인된 root 또는 Shizuku로 모든 명령을 실행합니다(기본 꺼짐, 파티션/SELinux/마운트/플래시는 계속 차단, ADB는 허용 목록 유지).
- - Termux와 유사한 네이티브 호출 추가(bxroot 엔진): Ubuntu에서 Android 네이티브 명령(`getprop`, `toybox`, `pm` 등)을 바로 실행할 수 있습니다(같은 이름의 guest 프로그램 우선, `bx-host <명령>`으로 명시 가능). Android 네이티브 shell은 `$BXROOT_ENTER`로 같은 Ubuntu 세션에 돌아갈 수 있습니다. 이 앱의 UID 그대로이며 권한 상승은 없고, 사용 가능한 시스템 명령은 Android 권한이 결정합니다.
- - HyperOS 등에서 1×1 시스템 글리프/배지 창(HyperDuo glyph, 노트 배지 등)이 TARGET_WINDOW_OCCLUDED를 유발해 좌표 탭과 스와이프를 완전히 사용할 수 없던 문제를 수정했습니다. 무시할 수 있을 만큼 작은 창은 가림 판정에 참여하지 않습니다(#212).
- - "앱 목록 형식을 확인할 수 없습니다"로 앱 종료가 차단될 때 오류에 첫 번째 예상 밖 행(마스킹·절단)을 덧붙이고 사용자/시스템 목록을 구분해 표시합니다. ROM별 pm 출력 차이를 파악하는 데 도움이 됩니다(#210 후속).
- - dsh-web-mobile 3.2.2, DSH rc.3, 로컬 및 LAN 접근과 10개 언어를 유지합니다. 휴대전화 검증은 계속 보류됩니다.
