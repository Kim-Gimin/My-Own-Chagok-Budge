# 빌드·테스트

## 준비

JDK 17, Android SDK Platform 36, Build Tools 36(aapt2/zipalign/apksigner/D8)이 필요합니다. 앱은 Java 8 대상이며 새 런타임 라이브러리를 추가하지 않았습니다. Android Studio/Gradle 래퍼 프로젝트와 로컬 PowerShell 빌드 스크립트를 제공합니다.

아래 경로는 각 PC에 맞게 바꾸세요. 공백/한글 경로를 처리하지 못하는 SDK 도구가 있으면 소스를 짧은 영문 임시 경로로 복사해 빌드하세요.

## 기존 앱 업데이트용 APK

```powershell
.uild-local.ps1 -Jdk 'C:\Tools\jdk-17' `
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
.	est-core.ps1 -Jdk 'C:\Tools\jdk-17' `
  -TemplateFile '.\templates\chagok-input-0.8.11.xlsx'
```

최신 0.8.11 기준 기존 검사는 1103개입니다. 회계/엑셀 10,000행/기초자산·원금 보호/계획·생활비/분류 정리/계획 XLSX를 검사합니다. 현재 이관 검증의 실제 결과는 [검증 기록](PRIVACY_VALIDATION.md)을 따릅니다.

## Android 통합 검사

`test-android.ps1`은 초기화가 가능한 **전용 에뮬레이터만** 사용하세요. 실제 사용자 폰과 실제 장부를 테스트에 사용하지 않습니다. `-Serial emulator-5584`처럼 에뮬레이터 serial을 요구합니다.

```powershell
.	est-android.ps1 -Jdk 'C:\Tools\jdk-17' `
  -AndroidJar 'C:\Android\platforms\android-36\android.jar' `
  -BuildTools 'C:\Android\build-tools\36.0.0' `
  -Adb 'C:\Android\platform-tools\adb.exe' `
  -Serial emulator-5584 -Mode full
```

다른 모드는 `planning`, `planselect`, `categories`, `calendarLiving`, `back`, `assetguard`, `recovery` 등입니다. 과거 버전 전용 baseline/upgrade 모드는 해당 APK와 fixture를 맞춰야 합니다. 키가 필요한 통합 빌드는 로컬 원본 키 설정을 먼저 준비하세요.


공개 예시·테스트는 가상 데이터만 사용합니다. 예전 문서·바이너리를 공개 이력에 다시 넣지 않습니다. 과거 클론 대신 OneDrive 밖의 새 클론을 사용하세요.
