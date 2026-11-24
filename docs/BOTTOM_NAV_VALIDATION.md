# 1.0.0 하단 메뉴 편집 — 변경·검증·한계

2026-11-24 / versionName 1.0.0 / versionCode 32 / DB5. 기준: `main` cd6b470(0.9.2 / code31).

## 사용자 요청

“메뉴에 있는 것들을 아래 있는 메뉴로 편집할 수 있는 기능”. 메뉴의 기능을 하단에 직접 배치하고 순서를 바꾸며, 취소와 기본 배치 복원을 함께 제공합니다.

## 변경 파일

| 파일 | 내용 |
|---|---|
| `BottomNav.java`(새) | 후보 10개, 기본 6개, 3~6개 검증, `BNV1:` 코덱, 추가/빼기/끌기 이동, 현재 화면 위치. Android 의존 없음 |
| `AppPreferences.java` | `bottomNav()` 읽기(손상 시 기본), `bottomNav(list)` 검증 후 `commit` 실패 시 예외 |
| `MainActivity.java` | 하단 바를 저장 구성으로 그림, 길게 누르면 편집, `openNavTarget` 공용 이동, 메뉴·설정에 ‘하단 메뉴 편집’, `customizeBottomNav` 대화상자, 아이콘 4종(budget/trend/wallet/split) |
| `UserGuidePage.java`, `docs/USER_GUIDE.md` | 하단 메뉴 편집 안내 |
| `tests/BottomNavTests.java`(새), `test-core.ps1` | JVM 304개 |
| `tests/android/BottomNavChecks.java`(새), `DeviceChecks`, `test-android.ps1` | 에뮬레이터 모드 `bottomnav` |
| `app/build.gradle` | 1.0.0 / code32 |
| `templates/chagok-input-1.0.0.xlsx` | 0.9.2 빈 양식과 바이트 동일(SHA256 d8349053…be1c) |

DB 스키마·마이그레이션, 거래/계획/기초자산/이동, XLSX/ZIP 코덱, 권한, 외부 라이브러리는 변경하지 않았습니다.

## 동작 계약

- 업데이트 직후 `bottom_nav`가 없으므로 0.9.2와 같은 결산·통계·홈·달력·내역·엑셀이 같은 라벨·아이콘·접근성 설명(“○○ 탭”)으로 표시됩니다. 기존 Android 검사의 “홈 탭/통계 탭/내역 탭/엑셀 탭” 탐색은 그대로 유효합니다.
- 편집 중 끌기·추가·빼기·기본 배치는 대화상자 안의 목록만 바꿉니다. ‘저장’에서만 기록하고, 저장 후 현재 화면과 스크롤을 유지한 채 다시 그립니다. ‘취소’·바깥 터치·뒤로는 미저장이며 진행 중인 끌기는 `CategoryDrag.cancel`로 정리합니다.
- 최소 3개에서 빼기, 최대 6개에서 추가는 안내만 하고 바꾸지 않습니다. 해당 버튼은 회색이며 접근성 설명에 이유를 붙입니다.
- 설정(6)·설명서(9)는 후보가 아닙니다. 두 화면은 원래 하단 바를 숨기고, 설정은 모든 화면의 상단 톱니로 열립니다.
- 하단에서 뺀 기능도 왼쪽 위 메뉴에 남습니다. 시작 화면 설정·뒤로 가기·PIN·백그라운드 복귀·파일 선택 복귀 흐름은 변경하지 않았습니다.

## 실행한 검사

- 변경 전: JVM 1,484개(빈 양식 0.9.2 포함) 통과.
- 변경 후: JVM 1,788개 = 기존 1,484 + BottomNavTests 304(빈 양식 1.0.0 포함) 통과. OpenJDK 17, Linux.
- 앱 전체 소스와 `tests/android/*.java`를 공개 AOSP `android-36` android.jar(숨김 API 포함 비공식 배포본)로 javac `-source 8 -target 8` 타입 검사 통과. 이 jar에는 `android.view.InputFilter`가 따로 있어 기존 `InputFilter` 참조가 모호해지므로, 검사용 임시 복사본에서만 `android.text.InputFilter`로 바꿔 컴파일했습니다. 저장소 소스는 바꾸지 않았으며 공식 SDK에서는 기존처럼 문제없습니다.

## 실행하지 못한 검사(PC에서 필요)

1. `build-local.ps1`(원본 키, `CHAGOK_KEYSTORE_PASSWORD`)로 1.0.0 APK 빌드·apksigner 인증서가 0.9.2와 같은지 확인.
2. `test-core.ps1 -TemplateFile templates/chagok-input-1.0.0.xlsx`.
3. 전용 에뮬레이터: `-Mode bottomnav`(취소 미저장, 최소 개수, 추가, 접근성 이동, 저장·화면 표시, 재생성, 기본 배치, 손상 값), 그리고 기존 `startup`·`back`·`settings`·`drag` 회귀. 다크 모드·큰 글꼴에서 5~6글자 라벨(“기초자산”, “해지·배분”) 잘림 확인.
4. 실제 S23 Ultra에서 끌기 느낌·하단 길게 누르기 확인(초기화 검사 금지).

실제 사용자 폰·개인 장부·백업은 사용하지 않았습니다.
