# MariaDB와 Hibernate 자동 schema 관리를 사용한다

- 상태: 채택
- 날짜: 2026-09-05

## 배경

프로젝트는 MySQL 8.0, MySQL Connector/J, Flyway migration과 Hibernate schema validation을 함께 사용해 왔다. 그러나 현재는 보존하거나 이관해야 할 실제 운영 데이터가 없는 개인 프로젝트이며, 여러 배포 환경 사이에서 schema version을 조정하거나 무중단 migration을 수행해야 하는 요구도 없다.

이 단계에서 운영 시스템 수준의 migration 이력과 rollback 절차를 유지하면 schema 변경 비용과 설정 복잡도가 프로젝트 규모에 비해 커진다. 데이터베이스를 MariaDB의 새로운 데이터 영역에서 시작하고 엔티티 매핑을 schema의 기준으로 삼아 현재 개발·배포 과정을 단순화할 필요가 있다.

## 결정

- 데이터베이스를 MySQL에서 MariaDB로 전환하고 로컬 Compose 이미지는 `mariadb:11.8.6`으로 고정한다.
- 애플리케이션은 MariaDB Connector/J와 `jdbc:mariadb` URL을 사용한다.
- Flyway 의존성, 설정과 기존 migration 파일을 제거한다.
- 실제 운영 데이터가 없는 현재 단계에서는 `local`, `prod` 프로필 모두 Hibernate `ddl-auto: update`로 schema를 관리한다.
- 격리된 H2 테스트는 `MODE=MariaDB`, `ddl-auto: create-drop`을 사용한다.
- 기존 MySQL 데이터 디렉터리는 MariaDB 데이터 영역으로 재사용하거나 자동 삭제하지 않는다.
- 실제 운영 데이터가 생기거나 여러 환경의 schema version 관리가 필요해지면 Flyway 같은 migration 도구와 `ddl-auto: validate` 전환을 다시 결정한다.

## 검토한 대안

- MySQL과 Flyway + `validate` 유지: 운영 데이터가 있는 시스템에는 안전하지만 현재 프로젝트에서는 migration 정합성 관리 비용이 더 크다.
- MariaDB로만 전환하고 Flyway 유지: 데이터베이스 전환 위험은 줄일 수 있지만 이번 결정의 핵심인 schema 관리 단순화를 달성하지 못한다.
- `local`만 `update`, `prod`는 `validate`: Flyway를 제거하면 새 배포 환경에 schema를 생성할 책임이 없어 현재 배포 방식과 맞지 않는다.

## 결과

- 엔티티 변경을 빈 MariaDB에 빠르게 반영할 수 있고 datasource 및 배포 설정이 단순해진다.
- 기존 MySQL 데이터의 이관과 Flyway 이력 보존은 지원하지 않는다.
- `ddl-auto: update`는 파괴적 변경, rollback, 배포 간 순서 제어를 보장하지 않으므로 실제 데이터가 생긴 뒤에는 사용할 수 없다.
- blue/green 인스턴스가 동시에 schema를 변경하지 않도록 schema 변경 배포 시 기동 순서를 관리해야 한다.
- 결정 배경과 구현 범위는 GitHub 이슈 [#33](https://github.com/juhwanz/eshop-refac/issues/33)에서 추적한다.
