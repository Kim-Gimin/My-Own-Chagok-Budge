# 빌드·테스트

## 준비

JDK 17, Android SDK Platform 36, Build Tools 36(aapt2/zipalign/apksigner/D8)이 필요합니다. 앱은 Java 8 대상이며 새 런타임 라이브러리를 추가하지 않았습니다. Android Studio/Gradle 래퍼 프로젝트와 로컬 PowerShell 빌드 스크립트를 제공합니다.

아래 경로는 각 PC에 맞게 바꾸세요. 공백/한글 경로를 처리하지 못하는 SDK 도구가 있으면 소스를 짧은 영문 임시 경로로 복사해 빌드하세요.

## 서명 비밀번호

실제 비밀번호를 스크립트·명령 기록·Git·Release에 적지 않습니다. 현재 PowerShell 프로세스에서 안전한 입력으로 설정하세요.

```powershell
$taskSigningPassword = Read-Host "서명 키 비밀번호" -AsSecureString
$env:CHAGOK_KEYSTORE_PASSWORD = [Net.NetworkCredential]::new("", $taskSigningPassword).Password
```

빌드/Android 검사 후 `Remove-Item Env:\CHAGOK_KEYSTORE_PASSWORD`로 프로세스 값을 지울 수 있습니다. 값이 없으면 빌드가 파일 생성 전에 중단됩니다. Gradle도 같은 환경변수를 사용합니다. APK 서명 도구는 환경변수 참조를 받아 실제 값을 명령 인자로 넣지 않습니다.

## 기존 앱 업데이트용 APK

```powershell
.\build-local.ps1 -Jdk 'C:\Tools\jdk-17' `
  -AndroidJar 'C:\Android\platforms\android-36\android.jar' `
  -BuildTools 'C:\Android\build-tools\36.0.0' `
  -Keystore 'C:\PrivateKeys\chagok-original.keystore' `
  -Output 'C:\Temp\chagok-build'
```

`-Keystore`는 Git 저장소 밖의 **기존 원본 서명키**를 지정해야 합니다. 원래 로컬 테스트 별칭과 키 설정을 사용하는 스크립트입니다. 원본 APK의 인증서 SHA256는 `2c19ecb9444fe90c0c9ea83378655b0d76cb57c2b14ecf43774a53e3f962fcde`입니다.

기존 스크립트는 키 파일이 없으면 새 키를 생성합니다. 이는 새 테스트 설치에만 사용할 수 있고 기존 설치의 업데이트 서명이 되지 않습니다. 원본 키가 없다면 업데이트용으로 배포하지 마세요. 기존 앱을 삭제해 해결하려 하면 내부 장부가 지워집니다.

Gradle 디버그 구성도 루트의 기존 `local-test.keystore`를 참조합니다. 필요한 경우 원본 키를 로컬에만 배치하고 `.gitignore`로 추적 제외를 확인하세요. 키·키 설정·인증 토큰을 커밋하지 않습니다.

## JVM 검사

```powershell
.\test-core.ps1 -Jdk 'C:\Tools\jdk-17' `
  -TemplateFile '.\templates\chagok-input-0.9.2.xlsx'
```

0.8.11 당시 기존 검사는 1103개입니다. 회계/엑셀 10,000행/기초자산·원금 보호/계획·생활비/분류 정리/계획 XLSX를 검사합니다. 현재 이관 검증의 실제 결과는 [검증 기록](PRIVACY_VALIDATION.md)을 따릅니다.

## Android 통합 검사

`test-android.ps1`은 초기화가 가능한 **전용 에뮬레이터만** 사용하세요. 실제 사용자 폰과 실제 장부를 테스트에 사용하지 않습니다. `-Serial emulator-5584`처럼 에뮬레이터 serial을 요구합니다.

```powershell
.\test-android.ps1 -Jdk 'C:\Tools\jdk-17' `
  -AndroidJar 'C:\Android\platforms\android-36\android.jar' `
  -BuildTools 'C:\Android\build-tools\36.0.0' `
  -Adb 'C:\Android\platform-tools\adb.exe' `
  -Serial emulator-5584 -Keystore 'C:\PrivateKeys\chagok-original.keystore' -Mode full
```

다른 모드는 `planning`, `planselect`, `categories`, `calendarLiving`, `back`, `assetguard`, `recovery` 등입니다. 과거 버전 전용 baseline/upgrade 모드는 해당 APK와 fixture를 맞춰야 합니다. 키가 필요한 통합 빌드는 로컬 원본 키 설정을 먼저 준비하세요.


공개 예시·테스트는 가상 데이터만 사용합니다. 예전 문서·바이너리를 공개 이력에 다시 넣지 않습니다. 과거 클론 대신 OneDrive 밖의 새 클론을 사용하세요.

## 0.8.12 시각 검사

`test-core.ps1`은 `TimeTests`를 포함합니다. Android `-Mode time`은 DB1~4 이관·실패 롤백·선택 취소·입력/수정·정렬·연결 시각을 검사하고 `timedark`는 다크 모드에서도 검사합니다. `timebefore`는 0.8.11 APK에 합성 자료를 만들며, 같은 서명 0.8.12를 `adb install -r`로 업데이트한 뒤 `timeupgrade`로 필드/rowid/feature_state/설정 보존과 자정을 비교합니다. 테스트는 전용 에뮬레이터에서만 실행합니다.

## 0.8.13 분류 순서 검사

JVM에 CategoryOrderTests를 포함합니다. Android order/orderdark는 추가·중복·빈 이름·유형별 이동·삭제/복원·입력/예산 선택·데이터 보존·손상된 설정 대체를 검사합니다. order 후 에뮬레이터의 앱 프로세스를 종료하고 orderreopen으로 디스크 저장을 확인합니다. orderbefore는0.8.12, 같은 서명0.8.13 업데이트 뒤 orderupgrade로 기존 DB/시각/계획/설정과 기본 순서 보존을 비교합니다.

## 0.8.14 끌기 검사

`test-core.ps1` 기본1,336개, 빈 양식을 함께 주면1,346개입니다. Android order/orderdark/orderreopen와 drag/dragdark를 사용합니다. drag 검사는32개 분류, 실제 입력 경로의 짧은 스크롤/길게 누르기, 양 끝 자동 스크롤, 높이가 다른 긴 이름, CANCEL/대화상자 종료, 저장 횟수, 접근성 노드 동작을 검사합니다. orderbefore는0.8.13에 사용자 순서·숨김·장부·계획·설정을 만들고, 같은 원본 서명0.8.14를 install -r로 올린 후 orderupgrade로 비교합니다. 실제 폰에서 실행하지 마세요.
