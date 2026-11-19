# 공개 자료와 Git 이력

공개 저장소에는 실제 사용자 사례·장부·백업·서명키·인증 정보를 올리지 않습니다. 예시는 가상 데이터만 사용합니다. 원본 과거 안내·검증 문서와 바이너리는 공개하지 않으며, 소스 태그의 예시도 정리했습니다.

소스 태그는 보관본을 재구성한 것으로, 앱 버전은 당시 번호를 유지하되 공개 예시가 수정되어 있습니다. 원본과 모든 바이트가 같다는 의미는 아닙니다. 0.8.11은 정리된 현재 앱의 새 배포 번호입니다. 배포일과 이관 커밋 날짜를 구분합니다.

APK는 Git 트리에 넣지 않습니다. 실제 사례가 없는 새 빌드를 검증한 뒤 Release에 APK·빈 양식·SHA256를 제공합니다. 원본 키나 개인 백업을 Release에 올리지 마세요.

## 이전 클론 사용 금지

이력 정리 전 클론을 pull/merge/push하면 삭제한 내용이 다시 올라갈 수 있습니다. 새 경로에 다시 clone하고 필요한 미반영 변경만 검토해서 옮기세요. 작업 폴더는 OneDrive 밖에 둡니다.

```powershell
git clone https://github.com/Kim-Gimin/My-Own-Chagok-Budge.git C:\dev\My-Own-Chagok-Budge
```

Git 이력 정리만으로 이미 만들어진 외부 복제본과 GitHub의 이전 SHA 캐시까지 제거된다고 보장할 수는 없습니다. 자세한 절차는 [GitHub 공식 안내](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository)를 참조하세요.
