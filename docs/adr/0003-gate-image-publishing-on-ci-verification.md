# CI 검증 성공 후 commit SHA 이미지 게시

- 상태: 채택
- 날짜: 2026-09-06

## 배경

기존 GitHub Actions는 빠른 테스트와 실행 JAR만 검증한 뒤 `main` push 이미지를 Docker Hub의 `latest` 태그로 게시했다. Testcontainers 기반 MariaDB·Redis 통합 테스트는 로컬에서 재현할 수 있지만 CI 검증에 포함되지 않아 데이터 정합성 변경이 병합 전에 실제 인프라 조합에서 검증되지 않았다.

검증과 이미지 게시가 하나의 job에 있어 게시 단계의 자격 증명 접근 경계도 불분명했다. 또한 `latest`만으로는 배포 결과물을 생성한 commit을 직접 식별하거나 같은 이미지를 다시 선택하기 어렵다.

## 결정

- `main` 대상 pull request와 `main` push에서 빠른 테스트·실행 JAR 검증 후 Testcontainers 통합 테스트를 실행한다.
- 통합 테스트는 GitHub-hosted runner의 Docker를 사용하며 별도 MariaDB·Redis service container를 두지 않는다.
- 검증과 이미지 게시를 별도 job으로 분리하고, 게시 job은 검증 job이 성공한 `main` push에서만 실행한다.
- Docker Hub에는 commit SHA를 불변 식별자로 사용한 태그를 게시하고, 기존 사용 편의를 위해 `latest`를 보조 태그로 함께 유지한다.
- workflow의 `GITHUB_TOKEN` 권한은 저장소 읽기로 제한한다. Docker Hub 자격 증명은 게시 job에서만 사용한다.
- 테스트 실패 시 원인 분석에 필요한 Gradle 테스트 결과와 보고서만 제한된 기간 동안 artifact로 보존한다.

## 검토한 대안

- `verifyChange`가 항상 `integrationTest`를 의존하게 한다: 하나의 명령으로 전체 검증을 강제할 수 있지만 Docker 없이 실행하는 빠른 로컬 검증의 기존 계약을 잃는다.
- workflow service container를 사용한다: 서비스 주소가 고정되는 장점이 있지만 테스트가 스스로 MariaDB·Redis 생명주기와 동적 접속 정보를 관리하는 Testcontainers 구성과 책임이 중복된다.
- 검증과 게시를 한 job에 유지한다: workflow가 짧지만 검증 실패와 게시 권한의 경계가 job 수준에서 드러나지 않는다.
- `latest`만 게시한다: 사용은 단순하지만 실행 중인 이미지와 원본 commit을 안정적으로 연결하거나 특정 결과물을 다시 선택할 수 없다.
- commit SHA 태그만 게시한다: 추적성은 가장 명확하지만 기존 `latest` 기반 실행 흐름과의 호환성이 깨진다.

## 결과

- 데이터 정합성 변경은 병합 전 실제 MariaDB·Redis 조합에서 자동 검증된다.
- 검증 실패 시 이미지가 게시되지 않고 Docker Hub 자격 증명도 사용되지 않는다.
- 게시된 이미지를 commit 단위로 식별하고 재선택할 수 있다.
- `latest`는 새 main 이미지가 게시될 때 이동하는 보조 포인터이므로 재현이나 배포 식별에는 commit SHA 태그를 사용해야 한다.
- CI 실행 시간과 Testcontainers 이미지 다운로드 비용이 늘어난다.
- 원격 서버 배포와 rollback 자동화는 이 결정의 범위에 포함하지 않는다.
- 구현과 완료 조건은 GitHub 이슈 [#15](https://github.com/juhwanz/eshop-refac/issues/15)에서 추적한다.
