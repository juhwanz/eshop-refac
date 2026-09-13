# 통합 테스트 데이터베이스를 MariaDB로 통일한다

- 상태: 채택
- 날짜: 2026-09-13
- 부분 대체: [ADR-0002](0002-use-mariadb-and-hibernate-schema-update.md)의 H2 테스트 결정

## 배경

ADR-0002는 빠르고 격리된 테스트를 위해 H2의 MariaDB 호환 모드와 `create-drop`을 선택했다. 이후 재고 CHECK, binary 멱등성 키, 유일 인덱스와 실제 DB 동시성처럼 MariaDB 동작을 직접 검증해야 하는 범위가 늘었다.

H2 호환 모드는 MariaDB의 DDL, 자료형, 제약과 트랜잭션 동작을 완전히 재현하지 않는다. 로컬과 CI가 같은 데이터베이스 엔진에서 통합 테스트를 실행하고, 테스트가 운영 코드의 MariaDB 매핑을 우회하지 않도록 DB 선택을 통일할 필요가 있다.

## 결정

- 데이터베이스가 필요한 통합·동시성 테스트는 Testcontainers의 `mariadb:11.8.6`을 사용한다.
- 테스트 프로필은 `ddl-auto: create-drop`을 유지해 빈 schema에서 엔티티 매핑을 생성하고 테스트 실행 후 제거한다.
- datasource URL, 사용자와 비밀번호는 Testcontainers가 동적으로 주입한다.
- H2 테스트 런타임 의존성과 H2 전용 설정은 유지하지 않는다.
- 단위·MVC slice 테스트는 실제 데이터베이스 없이 빠르게 실행하는 기존 분리를 유지한다.
- 개발자 로컬 MariaDB를 통합 테스트에 사용하지 않으며 Docker를 테스트 인프라 경계로 둔다.

## 검토한 대안

- H2의 MariaDB 호환 모드 유지: 실행은 빠르지만 MariaDB의 DDL, 자료형과 동시성 동작 차이를 놓칠 수 있다.
- H2와 MariaDB 테스트를 병행: 빠른 피드백과 실제 엔진 검증을 함께 얻지만 같은 영속성 시나리오와 설정을 두 벌로 관리해야 한다.
- 개발자 로컬 MariaDB 사용: 컨테이너 시작 비용은 없지만 로컬 데이터와 설정에 의존해 격리와 재현성이 떨어진다.

## 결과

- 로컬과 CI의 통합 테스트가 애플리케이션과 같은 MariaDB 계열의 DDL, 제약과 동시성 동작을 검증한다.
- H2 호환성 설정과 별도 런타임 의존성을 제거해 테스트 DB 기준이 하나가 된다.
- 통합 테스트 실행에는 Docker와 이미지 다운로드가 필요하고 단위 테스트보다 시작 시간이 길다.
- 빈 schema의 `create-drop` 검증은 기존 MariaDB에 `ddl-auto: update`가 안전하게 적용되는지 보장하지 않는다.
- 구체적인 테스트 분리와 실행 방법은 [테스트와 검증](../testing.md)을 참고한다. 관련 기반 작업은 Testcontainers 환경을 구축한 [#16](https://github.com/juhwanz/eshop-refac/issues/16)과 MariaDB로 전환한 [#33](https://github.com/juhwanz/eshop-refac/issues/33)에서 확인한다.
