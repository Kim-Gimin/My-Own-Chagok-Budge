# 개발 구조 — 0.8.11

| 파일/영역 | 책임 |
|---|---|
| `MainActivity.java` | 네이티브 화면·입력·달력/통계 월·파일 선택·뒤로 가기/PIN 흐름 |
| `Ledger.java`, `TransactionType.java` | 거래·유형·집계·중복 판정 |
| `LedgerDb.java` | SQLite DB5·원자 저장·기초자산/계획/연결 상태 |
| `OpeningAsset.java`, `AssetConsistency.java` | 기초자산과 시점별 원금 보호 |
| `SavingsMovement.java` | 해지·배분과 실제 금액/과거 형식 호환 |
| `PlanningData.java`, `LivingBudgetSelector.java` | 예산·목표·반복·즐겨찾기·생활비 선택 |
| `CategoryStore.java` 및 분류 정리 코드 | 유형별 사용자 분류·경계 공백 정리·복구 |
| `XlsxIO.java`, `BackupArchive.java` | 라이브러리 없이 XLSX와 ZIP 읽기·쓰기·검증 |
| `StatisticsData.java`, `HistoryData.java`, 차트 View | 통계/내역용 집계와 표현 |
| `AppPreferences.java`, `UserGuidePage.java` | 화면/잠금 설정·오프라인 설명서 |
| `tests/`, `tests/android/` | JVM/전용 에뮬레이터 통합 검사 |

거래와 계획/기초자산/화면 설정을 분리합니다. 생활비는 선택 지출만 별도 집계하며 전체 지출 통계를 바꾸지 않습니다. `calendarMonth`는 달력, `month`는 기존 통계·내역·계획에 쓰므로 섞지 않습니다.

SQL·계획 코덱·만기 연결 형식을 바꿀 때는 구버전 읽기/마이그레이션과 실패 롤백을 함께 작성하세요. 사용자 데이터를 자동 초기화하거나 원금 보호 검사를 우회하지 않습니다. 자세한 계약은 [FEATURES](../FEATURES.md)와 [AI 인계](AI_HANDOFF.md)를 따릅니다.

## 기록 시각 (0.8.12)

`Entry.date`와 `Entry.time`을 분리합니다. 시각은 기기 현지 시각의 HH:mm:ss이며 시간대 변환용 타임스탬프가 아닙니다. 자정의 fingerprint는 구버전과 동일하며 다른 시각은 중복 판정에 포함합니다. DB는 (date,time) 인덱스를 사용합니다. 자산 검증은 기존 일자 단위를 유지합니다.

`MainActivity.recordDateTime`은 임시 선택을 달력 → 시계로 넘기며 시계 확인 시에만 초안에 반영합니다. `SavingsMovement`의 연결 거래는 모두 같은 시각입니다. 기초자산과 계획 일정은 날짜 단위를 유지합니다.
