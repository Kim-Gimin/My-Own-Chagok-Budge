# 차곡가계부 · My Own Chagok Budget

수입·지출·저축·투자와 월 생활비를 관리하는 오프라인 안드로이드 가계부입니다. **최신 공개 버전은 0.9.0 / versionCode 29 / DB5**이며 Galaxy S23 Ultra를 기준으로 개발했습니다.

## 주요 문서

- [사용 설명서](docs/USER_GUIDE.md) · [기능·저장 계약](FEATURES.md)
- [버전별 변경·소스 태그](docs/RELEASES.md) · [변경 이력](CHANGELOG.md)
- [빌드·검증](docs/BUILD.md) · [실행 검증](VALIDATION.md)
- [데이터·백업 안전성](docs/DATA_SAFETY.md) · [개발 구조](docs/ARCHITECTURE.md)
- [AI 개발 인계](docs/AI_HANDOFF.md) · [작업 규칙](AGENTS.md)
- [공개 자료 범위](docs/PUBLIC_HISTORY.md) · [최신 Release](https://github.com/Kim-Gimin/My-Own-Chagok-Budge/releases/latest)

## 기능

| 영역 | 지원 기능 |
|---|---|
| 기록 | 수입·지출·저축·투자·저축회수, 현재·지정 시각, 금액 키패드 → 분류·메모 입력 |
| 홈·달력 | 홈 재진입, 현재 달·오늘 선택, 월 스와이프, 선택 날짜 + 입력 |
| 내역·통계 | 메모 검색·분류 정렬, 회전 원형 차트, 월/연/올해 누적/지정 기간, 결산·추이 |
| 자산 | 기초자산, 해지·배분, 시점별 원금 보호·원자 롤백 |
| 생활관리 | 월 예산·반영 분류, 달력 생활비 보기, 목표·반복·즐겨찾기 |
| 백업 | 전체 XLSX의 거래·기초자산·계획, 연결 정보까지 보관하는 ZIP |
| 설정 | 상세 분류 추가·순서 변경·삭제·복원, 다크 모드·글꼴·홈 표시·시작 화면·PIN·설명서·전체 초기화 |

[릴리스 목록](https://github.com/Kim-Gimin/My-Own-Chagok-Budge/releases)에는 0.1.0부터 총33개 버전·보관판이 있습니다. 0.8.10 이전은 변경 내용과 소스 다운로드를 제공하는 보관용 릴리스이며, 설치 APK는0.8.11 이후 배포물에서 제공합니다. 0.7.0·0.8.0은 복구 소스로 표시합니다.

## 공개 데이터 원칙

**실제 사용자 사례·금액·장부·백업은 공개하지 않습니다.** 설명서, 앱 도움말, 엑셀 예시와 테스트는 가상 데이터만 사용합니다. 과거 소스 태그도 같은 기준으로 정리했습니다. 원본 과거 문서·APK·양식은 공개 자료에서 제외했습니다.

0.9.0은 월별 추이·전월 비교 화면에 누적 지출 속도, 예산 권장 속도선, 전월 같은 기간 비교와 예산 초과·잔액 안내를 추가합니다. 첨부 APK는 기존 0.8.14와 같은 서명 인증서를 사용하며 versionCode가 28에서 29로 올라 기존 앱 위에 설치해 업데이트할 수 있습니다. 앱을 삭제하지 않고 APK를 설치하세요. [서명 대조 결과](docs/SIGNATURE_VALIDATION_0.9.0.md)를 확인할 수 있습니다.

APK는 Git 소스 이력 대신 [GitHub Release](https://github.com/Kim-Gimin/My-Own-Chagok-Budge/releases/latest)에 첨부합니다. 최신 빈 입력 양식은 [chagok-input-0.9.0.xlsx](templates/chagok-input-0.9.0.xlsx)입니다.

## 개발

Java 8 대상, Android minSdk26 / compile·targetSdk36, JDK17입니다. 배포 빌드는 `build-local.ps1`을 사용하며 Gradle wrapper8.11.1도 포함합니다. XLSX는 `XlsxIO`로 직접 처리하고 새 외부 앱 라이브러리를 추가하지 않았습니다. 권한은 `POST_NOTIFICATIONS`이며 폰트의 SIL OFL 문구를 포함합니다.

서명키와 비밀번호는 Git 저장소에 없습니다. 빌드할 때 서명 비밀번호를 현재 프로세스의 `CHAGOK_KEYSTORE_PASSWORD` 환경변수로 설정하세요. 명령문·로그·문서에 실제 값을 적지 않습니다. 원본 키는 로컬에 별도로 보관하고 빌드 시 명시적으로 지정하세요. 원본 키 없이 새로 만든 APK는 기존 설치의 업데이트가 될 수 없습니다. [빌드 안내](docs/BUILD.md)를 따릅니다.

작업 클론은 OneDrive 밖에 두세요. 이력 정리 전 클론을 merge/push하지 말고 다시 clone합니다. 역사 태그는 원래 개발 커밋이 아니라 소스 스냅샷을 재구성한 것입니다. 배포일은 문서에 기록하며 커밋 작성일과 구분합니다.
