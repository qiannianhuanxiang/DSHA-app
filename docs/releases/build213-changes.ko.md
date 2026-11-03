# DSHA 0.2.1-rc.3-bxroot.11-dsh0.2.1-rc.3 / build213

- 시작 속도 추가 향상: 이전 검토 표시가 없으면 "이전 검토 표시 마이그레이션" 컨테이너 프로세스를 더 이상 실행하지 않습니다(약 0.5초). DSH가 Node 모듈 컴파일 캐시(`/root/.cache/dsha-node-compile`, 소스가 바뀌면 자동 무효화, `NODE_DISABLE_COMPILE_CACHE=1`로 끌 수 있음)를 사용해 시작할 때마다 불러오는 약 180개 패키지의 파싱·컴파일 시간을 줄입니다.
- 첫 압축 해제 속도 향상: 새 환경을 풀 때 파일마다 강제로 디스크에 쓰지 않고(약 8만 번의 fsync) 마지막에 한 번만 동기화합니다. Linux 5.8 이상 커널에서만 사용하며, 쓰기 오류는 여전히 감지되고 도중에 전원이 꺼지면 후보 환경을 버리고 다시 풀며 게시된 환경에는 영향이 없습니다.
- 시작 속도 향상: 터미널/Web 실행 준비 결과를 앱 재시작 후에도 캐시합니다. APK, 환경, 관리 파일이 바뀌지 않았다면 콜드 스타트 후 첫 터미널에서 내장 파일 103개를 바이트 단위로 다시 비교하거나 패치 체인을 다시 실행하지 않으며, 관리 파일이 하나라도 바뀌면 자동으로 다시 준비합니다.
- 네이티브 기능 명령 추가: Ubuntu에서 Termux:API 스타일의 `termux-clipboard-get/set`, `termux-notification`, `termux-toast`, `termux-vibrate`, `termux-torch`, `termux-open-url`, `termux-share`, `termux-location`, `termux-sensor`, `termux-battery-status`, `termux-dialog`를 바로 쓸 수 있습니다. `dsha-am start/broadcast`는 DSHA 자체 신원으로 화면을 열거나 브로드캐스트를 보냅니다(root 불필요, 화면 확인 정책에 따라 확인). `dsha-su`는 설정 → 기기 권한에서 「제한 없는 기기 셸」을 켜면 승인된 root 또는 Shizuku로 모든 명령을 실행합니다(기본 꺼짐, 파티션/SELinux/마운트/플래시는 계속 차단, ADB는 허용 목록 유지).
- - Termux와 유사한 네이티브 호출 추가(bxroot 엔진): Ubuntu에서 Android 네이티브 명령(`getprop`, `toybox`, `pm` 등)을 바로 실행할 수 있습니다(같은 이름의 guest 프로그램 우선, `bx-host <명령>`으로 명시 가능). Android 네이티브 shell은 `$BXROOT_ENTER`로 같은 Ubuntu 세션에 돌아갈 수 있습니다. 이 앱의 UID 그대로이며 권한 상승은 없고, 사용 가능한 시스템 명령은 Android 권한이 결정합니다.
- - HyperOS 등에서 1×1 시스템 글리프/배지 창(HyperDuo glyph, 노트 배지 등)이 TARGET_WINDOW_OCCLUDED를 유발해 좌표 탭과 스와이프를 완전히 사용할 수 없던 문제를 수정했습니다. 무시할 수 있을 만큼 작은 창은 가림 판정에 참여하지 않습니다(#212).
- - "앱 목록 형식을 확인할 수 없습니다"로 앱 종료가 차단될 때 오류에 첫 번째 예상 밖 행(마스킹·절단)을 덧붙이고 사용자/시스템 목록을 구분해 표시합니다. ROM별 pm 출력 차이를 파악하는 데 도움이 됩니다(#210 후속).
- - dsh-web-mobile 3.2.2, DSH rc.3, 로컬 및 LAN 접근과 10개 언어를 유지합니다. 휴대전화 검증은 계속 보류됩니다.
