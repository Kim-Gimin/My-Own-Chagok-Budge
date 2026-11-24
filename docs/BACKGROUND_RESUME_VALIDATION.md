# 백그라운드 복귀 검증 — 0.9.2 / 2026-11-24

## 원인과 변경

`onUserLeaveHint`에서 다른 앱으로 이동할 때 홈 복귀를 예약하고 `onResume`에서 강제 이동했습니다. 설정·설명서도 pause/stop 뒤 복귀 시 홈으로 바뀌었습니다. 이 처리를 제거했습니다.

- 같은 Activity로 복귀하면 화면을 다시 만들지 않아 탭·달·필터·스크롤·입력창이 유지됩니다.
- PIN은 기존처럼 재확인합니다. 잠금 해제 후 살아 있는 화면을 재사용하되 잠금 중 자료 변경으로 render가 요청되면 해제 후 갱신합니다. 가져오기 대기 결과·연간 누적 통계의 날짜 갱신을 유지합니다.
- savedInstanceState가 있으면 Activity 재생성 시 화면·달·내역 표시 개수·스크롤을 복원합니다. 백업 알림은 새 실행일 때 파일 화면을 열며, 복원된 화면을 덮어쓰지 않습니다.
- 새 실행은 기본 홈이며 사용자 시작 화면 설정을 우선합니다. 앱 종료·최근 앱에서 제거한 뒤의 새 실행과 일시적 전환을 구분합니다.

## 변경 파일

- MainActivity.java: 복귀·화면 복원·PIN 뒤 이어가기.
- UserGuidePage.java: 앱 안 설명서를 새 동작으로 갱신.
- tests/android/StartupChecks.java, CalendarStartupChecks.java: 전체 화면 복귀·객체 유지·스크롤·입력창/PIN·재생성·새 실행/백업 알림·달력·재무 자료 보존 검사 수정.
- app/build.gradle: 0.9.2/code31. DB·회계·권한·라이브러리는 변경하지 않음.
- .gitignore, templates/chagok-input-0.9.2.xlsx, templates/SHA256SUMS.txt: 내용이 같은 빈 양식 제공.
- README/FEATURES/CHANGELOG/VALIDATION, docs/USER_GUIDE/AI_HANDOFF/BUILD/RELEASES, docs/releases/0.9.2.md, AGENTS 및 이 문서: 버전·동작·검증·개발 인계 갱신.

## 검증과 한계

APK SHA256: `57424ee0ff7006d7600313cdd446f530426f0e4430becfadf2f40718cc991a37`.
서명 인증서 SHA256: `2c19ecb9444fe90c0c9ea83378655b0d76cb57c2b14ecf43774a53e3f962fcde` (기존 배포와 동일).

- 변경 전후 빈 양식 포함 JVM1,484개 통과. 기존 회계·10,000행 XLSX·원금 보호·계획/백업·분류·시각·예산/전체 그래프 검사를 재실행했습니다. 이는 Android 생명주기 실행 검사가 아닙니다.
- 변경 전후 JDK17/SDK36/Build Tools36으로 원본 키 APK 빌드 성공. 한글 경로 제약 때문에 영문 경로의 소스 스냅샷을 사용했습니다.
- Android 회귀 검사 전체 소스를 앱 클래스와 SDK에 맞춰 javac로 컴파일했습니다. `test-android.ps1 -Mode startup` 및 `-Mode calendar`가 변경된 실행 검사입니다.
- 연결 기기·Android 에뮬레이터가 없어 실제 앱 전환/잠금/스크롤/대화상자/재생성·프로세스 복원 실행은 확인하지 못했습니다. 실제 사용자 폰이나 장부를 검사에 사용하지 않았습니다.
- Activity가 유지되는 동안의 입력창을 보존하며, 프로세스 종료 후 저장하지 않은 대화상자 초안을 새로 직렬화하지 않았습니다.

설계 참고: [Android 공식 Activity 생명주기·상태 복원 안내](https://developer.android.com/guide/components/activities/activity-lifecycle).
