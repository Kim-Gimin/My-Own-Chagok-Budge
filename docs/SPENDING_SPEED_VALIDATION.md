# 지출 속도 검증 — 0.9.0

2026-11-23 / versionCode29 / DB5.

- `test-core.ps1`: 11개 JVM 테스트 묶음, 총 1,348개 검사 통과.
- `assembleDebug`: JDK 17, Android SDK 36, Build Tools 35 환경에서 성공.
- `apksigner verify --verbose`: APK Signature Scheme v2 검증 통과.
- 지출 속도 계산은 현재 월의 오늘까지 누적액, 전월 같은 날짜까지 누적액, 전체 예산의 일별 권장액을 각각 검증했습니다.
- 생활비 반영 분류가 명시적으로 비어 있는 경우 지출 속도 계산도 0개 분류를 반영합니다.

실제 사용자 장부·실제 휴대폰은 사용하지 않았습니다. 배포 APK의 서명 인증서를 기존 0.8.14 배포 APK 및 로컬 원본 키의 인증서와 직접 대조해 동일함을 확인했습니다. 패키지는 같고 versionCode는 28 → 29입니다. APK 파일명만 `chagok-0.9.0.apk`로 정리했으며 재빌드·재서명하지 않았습니다. 원본 키와 비밀번호는 저장소에 넣지 않았습니다. [서명 대조 결과](SIGNATURE_VALIDATION_0.9.0.md)를 참고하세요.
