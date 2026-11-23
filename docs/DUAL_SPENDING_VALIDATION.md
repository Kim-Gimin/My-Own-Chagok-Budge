# 예산·전체 지출 그래프 검증 — 0.9.1

2026-11-23 / versionCode30 / DB5.

## 변경 파일

- `MainActivity.java`: 예산 기준/전체 지출 그래프를 함께 표시, 적용 예산 선택, 화면 상태 복원, 각각의 합계·비교 안내.
- `SpendingSpeedData.java`: 예산의 집계 범위와 전체 지출 집계를 분리. 전체 예산만 생활비 선택을 따르고 분류별 예산은 해당 분류만 사용.
- `SpendingSpeedView.java`: 그래프의 접근성 설명에 범위 추가, 카드 안의 좁은 화면에서 범례를 세로로 표시.
- `tests/SpendingSpeedTests.java`, `test-core.ps1`: 두 집계의 독립성·회계 범위·기간 비교·데이터 보존 검사 추가.
- `app/build.gradle`: 0.9.1/code30. DB·권한·라이브러리는 변경하지 않음.
- `.gitignore`, `templates/chagok-input-0.9.1.xlsx`, `templates/SHA256SUMS.txt`: 기존과 바이트가 같은 빈 양식을 새 버전 이름으로 제공.
- `README.md`, `FEATURES.md`, `CHANGELOG.md`, `VALIDATION.md`, `docs/USER_GUIDE.md`, `docs/BUILD.md`, `docs/RELEASES.md`, `docs/AI_HANDOFF.md`, `docs/releases/0.9.1.md`, 이 문서: 기능·사용·검증·배포·인계 정리.

## 실행 결과

- 변경 전: 빈 양식 포함 기존 JVM1,358개와 원본 키 APK 빌드 통과.
- 변경 후: 기존1,358개 + 추가126개 = 총1,484개, 12개 검사 묶음 통과. 기존 10,000행 XLSX 왕복·계획/백업·원금 보호·생활비 선택·시각·분류 순서 검사를 포함.
- 예산에서 일부/전부/0개 분류 선택, 예산 밖 지출 포함 여부, 개별 분류 예산의 독립성, 이 달 예산 우선, 없는 예산/내역, 수입·저축·투자·저축회수 제외를 검사.
- 현재 월의 미래 내역 제외, 전월 같은 일수·전월 전체 금액, 윤년 2월/31일 차이, 최대 거래 금액, 기존 계획 문자열과 내역의 보존을 검사.
- JDK17, SDK36, Build Tools36에서 `build-local.ps1` 성공. aapt2의 한글 경로 제약으로 소스 스냅샷을 영문 경로에 복사해 전후 빌드.
- APK Signature Scheme v2/v3 검증 통과. 패키지 `kr.chagok.ledger`, versionName0.9.1/code30 확인.
- 인증서 SHA256는 기존 0.8.14/0.9.0과 동일: `2c19ecb9444fe90c0c9ea83378655b0d76cb57c2b14ecf43774a53e3f962fcde`.
- APK SHA256: `173afb5d4c7db05f2fd39cef03b2add5ad0ed3f11244a1a38d37982dcd0b7680`.

## 한계

이 PC에는 연결된 기기와 Android 에뮬레이터가 없어 새 화면의 실제 실행·렌더링·터치·회전·다크 모드·큰 글꼴 검사는 수행하지 못했습니다. 기존 버전의 Android 실행 검증을 이번 버전의 결과로 주장하지 않습니다. 실제 사용자 폰·장부를 검사에 사용하지 않았습니다.
