# 0.9.0 APK 서명 대조 — 2026-11-23

0.9.0 배포 APK는 기존 0.8.14 배포 APK와 같은 서명 인증서를 사용합니다. 기존 앱을 삭제하지 않고 업데이트할 수 있는 패키지·버전·서명 조건을 확인했습니다.

| 항목 | 기존 배포 APK | 0.9.0 배포 APK |
|---|---|---|
| 패키지 | kr.chagok.ledger | kr.chagok.ledger |
| versionName | 0.8.14 | 0.9.0 |
| versionCode | 28 | 29 |
| 인증서 SHA256 | 아래 공통 값 | 아래 공통 값 |

공통 인증서 SHA256:

`2c19ecb9444fe90c0c9ea83378655b0d76cb57c2b14ecf43774a53e3f962fcde`

- 기존 APK는 개인 저장소의 v0.8.14 Release에서 내려받았습니다.
- `apksigner verify --print-certs`로 두 APK의 서명을 검증하고 인증서를 비교했습니다. 로컬 원본 키에서 내보낸 공개 인증서의 SHA256도 같습니다. 키·비밀번호는 공개하지 않았습니다.
- 0.9.0은 APK Signature Scheme v2 검증을 통과했습니다. `aapt2 dump badging`으로 패키지와 버전을 대조했습니다.
- 0.9.0 APK SHA256: `30a862b1f5004fefa2cc925b55460161bbd49cf96474ac489130de1b08db5f55`.
- 이미 검증한 `chagok-0.9.0-spending-speed-debug.apk`를 `chagok-0.9.0.apk`로 이름만 정리했습니다. 파일 바이트·앱 코드·서명은 같습니다. 재빌드·재서명과 기능 검사 재실행은 하지 않았습니다.
- 실제 사용자 폰에서 설치·실행한 검사는 아닙니다. 기존 JVM·빌드 검증 범위는 [지출 속도 검증](SPENDING_SPEED_VALIDATION.md)을 따릅니다.

앞서 게시한 ‘임시 키라 기존 설치 업데이트 불가’ 안내는 인증서 대조 없이 작성한 잘못된 설명이므로 정정합니다. v0.9.0 태그와 앱 코드는 유지하고 main의 문서·Release 안내·첨부 파일명을 바로잡았습니다. Release에 첨부한 소스 ZIP은 이 문서 정정까지 포함합니다.
