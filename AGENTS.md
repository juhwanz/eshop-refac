# AGENTS.md

## 목적

- 이 파일은 이 저장소에서 AI와 페어 프로그래밍할 때 지켜야 할 작업 원칙을 정의한다.
- 사용자를 최종 의사결정권자로 대한다. 중요한 선택에는 근거와 장단점을 설명하고, 범위가 분명한 요청은 스스로 구현하고 검증한다.
- 별도 요청이 없으면 진행 상황, 발견 사항, 최종 결과를 한국어로 전달한다.
- 기존 아키텍처와 비즈니스 불변조건을 보존하는 작고 검토 가능한 변경을 우선한다.

## 프로젝트 개요

- Java 21, Spring Boot, Gradle 기반의 단일 모듈 이커머스 백엔드다.
- 기본 패키지는 `com.project.eshop_refact`다.
- 핵심 관심사는 재고 정합성, 주문 멱등성, 동시성 제어, 조회 가용성, 캐시 정합성, 깊은 페이지 조회 성능이다.
- 운영형 데이터베이스는 MySQL이며 Redis는 캐시, 분산 락, 멱등성, 대기열, ShedLock에 사용한다.
- JPA/Hibernate, QueryDSL, Flyway, Spring Security, JWT, Actuator, SpringDoc을 사용한다.

## 저장소 구조

- `src/main/java/com/project/eshop_refact/domain`: 주문, 상품, 사용자, 대기열 도메인 코드
- `src/main/java/com/project/eshop_refact/global`: 공통 설정, 보안, 예외, 인터셉터, 응답 타입
- `src/main/resources/db/migration`: 버전이 부여된 Flyway 마이그레이션
- `src/test/java/.../controller`: Spring MVC 슬라이스 테스트
- `src/test/java/.../service`, `.../domain`: 서비스 단위 테스트와 도메인 테스트
- `src/test/java/.../integration`: H2와 Redis를 사용하는 통합·동시성 테스트
- `src/test/java/.../stressTest`: 로컬 데이터 및 부하 테스트 준비 도구이며 일반 단위 테스트가 아님

## 페어 프로그래밍 절차

1. 수정 전에 관련 운영 코드, 테스트, 설정과 `git status`를 확인한다.
2. 요구사항이 모호하면 가정을 밝힌다. 저장소에서 안전하게 추론 가능한 사소한 사항 때문에 작업을 멈추지 않는다.
3. 버그 수정은 원인을 먼저 규명하고 가능하면 회귀 테스트를 추가하거나 보완한다.
4. 요청을 해결하는 가장 작은 단위의 일관된 변경을 구현한다.
5. 변경 지점과 가장 가까운 테스트부터 실행하고 위험도에 따라 검증 범위를 넓힌다.
6. 최종 diff에서 무관한 수정, 비밀정보, 생성 파일, 런타임 데이터가 섞이지 않았는지 확인한다.
7. 변경 동작, 수행한 검증, 실패하거나 생략한 검사, 남은 위험을 보고한다.

## 변경 원칙

- 작업 트리가 더럽다면 사용자의 기존 변경을 보존한다. 무관한 파일을 복원하거나 스테이징하거나 커밋하지 않는다.
- 요청과 관계없는 리팩터링, 전체 포맷 변경, 패키지 이동, 의존성 업그레이드를 하지 않는다.
- 시스템 Gradle 대신 항상 Gradle Wrapper인 `./gradlew`를 사용한다.
- 운영 의존성 추가가 아키텍처나 운영에 영향을 줄 수 있으면 먼저 필요성과 대안을 설명한다.
- `build/generated/querydsl` 아래의 QueryDSL 생성 코드를 직접 수정하지 않는다.
- `mysql-data/` 아래의 런타임 데이터를 수정하거나 Git에 추가하지 않는다.
- `build/`, IDE 메타데이터, `.DS_Store`, `__pycache__/`, 로그, 토큰, 로컬 비밀 설정을 커밋하지 않는다.
- 사용자가 명시적으로 요청하지 않으면 커밋하거나 푸시하지 않는다. 요청받은 경우에도 작업 관련 경로만 포함하고 커밋과 대상 브랜치를 보고한다.

## Java와 빌드 설정

- 프로젝트 기준 Java 버전은 21이다.
- Java 기준 버전을 변경할 때는 다음 파일을 함께 맞춘다.
  - `build.gradle`
  - `Dockerfile`
  - `.github/workflows/deploy.yml`
  - `README.md`
- 컴파일과 테스트가 선언된 JDK를 사용하도록 Gradle toolchain 설정을 유지한다.
- 의존성 주입은 생성자 주입을 사용한다. 수정 대상 코드에 다른 이유가 없다면 기존 Lombok 사용 방식을 따른다.
- 기존 Java 포맷과 패키지 규칙을 따르고 wildcard import를 사용하지 않는다.

## 도메인과 트랜잭션 불변조건

- 서비스는 가능한 경우 `@Transactional(readOnly = true)`를 기본값으로 사용하고 상태 변경 메서드에 `@Transactional`을 적용한다.
- 엔티티의 상태는 공개 setter 대신 도메인 메서드로 변경한다.
- 재고는 음수가 될 수 없으며 동시 주문에서도 실제 재고보다 많이 판매되어서는 안 된다.
- Redis 락 대기 중 DB 커넥션을 점유하지 않도록 분산 락은 DB 트랜잭션 진입 전에 획득한다.
- Redisson 락은 실제로 획득했고 현재 스레드가 소유한 경우에만 `finally`에서 해제한다.
- `InterruptedException`을 잡으면 `Thread.currentThread().interrupt()`로 interrupt 상태를 복구한다.
- 대기열 정리 책임을 보존한다. 해당 락 처리 경로가 책임지는 사용자만 제거한다.
- 주문 취소 등 사용자 범위의 상태 변경 전에는 주문 소유권을 검증한다.

## 멱등성과 캐시 정합성

- 주문 멱등성 키는 `setIfAbsent` 같은 Redis 원자 연산으로 선점한다.
- 처리가 완료된 동일 요청은 새 주문을 만들지 않고 저장된 응답을 반환한다.
- 주문 처리 실패 시 처리 중인 멱등성 키를 제거해 안전한 재시도를 허용한다.
- 멱등성 키는 사용자와 요청 키 범위로 구성하고 명시적인 TTL을 둔다.
- DB 트랜잭션이 커밋되기 전에 상품 캐시를 제거하지 않는다.
- 대체 구현이 동일한 롤백 안전성을 보장하지 않는 한 `ProductCacheEvictEvent`와 `@TransactionalEventListener(AFTER_COMMIT)` 구조를 유지한다.

## API, 보안, 오류 처리

- 컨트롤러는 얇게 유지하고 비즈니스 판단은 서비스나 도메인 객체에 둔다.
- API 경계에서는 엔티티를 직접 노출하지 않고 요청·응답 DTO를 사용한다.
- 기존 `ApiResponse`, `ErrorResponse`, `BusinessException`, `ErrorCode` 규칙을 따른다.
- 컨트롤러 경로나 서비스 메서드를 수정할 때 인증과 인가 검사를 보존한다.
- 실제 DB 비밀번호, JWT 비밀키, 토큰, Docker 인증정보, 배포 키를 추적 파일에 추가하지 않는다.
- 설정 예시는 placeholder 또는 환경변수 참조만 사용한다.

## 영속성, QueryDSL, Flyway

- MySQL 스키마는 Flyway가 관리하고 local/prod 성격의 프로필에서는 Hibernate `ddl-auto`를 `validate`로 유지한다.
- 스키마 변경은 `V2__add_order_index.sql`처럼 새 마이그레이션으로 추가한다.
- 이미 적용된 버전 마이그레이션은 수정하지 않고 새 버전으로 전진 적용한다.
- local 또는 prod 스키마 변경에 `ddl-auto: update`, `create`, `create-drop`을 사용하지 않는다.
- H2의 `create-drop`은 격리된 테스트 설정에서만 사용한다.
- 컬렉션 fetch join과 pageable을 함께 사용해 메모리 페이징이 발생하지 않도록 한다.
- No-offset 페이지 조회를 변경할 때 커서 방향, 결정적인 정렬, 경계값, 지원 인덱스를 함께 검증한다.
- 성능 쿼리를 변경할 때 결과 정합성과 쿼리 수·형태를 모두 확인한다.

## 테스트 전략

### 빠른 검증

- 변경 코드와 가장 가까운 테스트를 먼저 실행한다.
- 일반적으로 실제 Redis나 MySQL 없이 실행할 수 있는 테스트 묶음은 다음과 같다.

```bash
./gradlew unitTest
```

- `test`와 `unitTest`는 같은 안전한 테스트 묶음을 실행한다.
- 컴파일, 빠른 테스트, 실행 가능한 JAR를 함께 확인하려면 `./gradlew verifyChange`를 사용한다.

### 통합·동시성 테스트

- 전체 Spring Context를 사용하는 테스트는 `localhost:6379`의 Redis가 필요할 수 있다. `local` 프로필이 명시되지 않은 테스트 데이터는 H2를 사용한다.
- 통합 검증에 Redis가 필요하면 필요한 서비스만 실행한다.

```bash
docker compose up -d redis
./gradlew integrationTest
```

- Redis 연결 실패를 Java 또는 애플리케이션 코드 호환성 실패로 설명하지 않는다.
- 동시성 테스트 실패 시 timeout이나 스레드 수를 바꾸기 전에 락 범위, 트랜잭션 경계, 공유 상태 정리, executor 종료를 조사한다.
- 테스트를 통과시키기 위해 assertion을 약화하거나 동시성을 낮추거나 임의의 sleep을 추가하지 않는다.

### 부하 테스트 준비 도구

- `src/test/java/.../stressTest` 아래 클래스는 `local` 프로필로 개발자 MySQL에 많은 데이터를 기록할 수 있다.
- 사용자가 부하 테스트 준비를 명시적으로 요청하거나 DB 변경을 승인하지 않으면 실행하지 않는다.
- 스트레스 및 대량 데이터 테스트는 기본 `test`에서 제외되어 있다.
- 실행 승인을 확인한 뒤에만 `./gradlew stressTest -PallowStressTest`를 사용한다.

### 빌드 검증

- 변경 범위에 맞으면 다음 명령을 사용한다.

```bash
./gradlew classes
./gradlew bootJar -x test
```

- 필요한 외부 서비스가 없다면 안전한 컴파일이나 패키징 검증은 계속 수행하고 인프라 제약을 정확히 보고한다.

## Codex 자동화

- Java, Gradle, 설정, Flyway 변경에는 저장소 Skill인 `eshop-change-verification`을 활용한다.
- 구현 후 가까운 테스트를 실행하고, 작업으로 인한 실패면 원인을 수정한 뒤 같은 검증을 다시 실행한다.
- 외부 서비스 부재, 사용자 데이터 변경 가능성, 요청 범위를 벗어난 실패가 있으면 자동 반복을 멈추고 정확한 상태를 보고한다.
- 저장소의 `Stop` Hook은 종료 전에 whitespace 오류, 민감 파일 추적, 새 비밀정보 패턴을 검사한다.
- Hook 검사를 통과했다는 사실이 테스트 실행을 대신하지 않는다.

## Git과 작업 완료

- 커밋 전에 `git diff --check`를 실행하고 작업 관련 diff를 정확히 검토한다.
- 동작이나 유지보수 결과를 설명하는 간결한 커밋 메시지를 사용한다.
- 인덱스에 이미 올라간 변경이라도 현재 작업과 무관하면 커밋하지 않는다.
- 사용자가 명시적으로 요청하지 않으면 히스토리를 재작성하거나 force push하거나 브랜치를 삭제하지 않는다.
- 완료 보고에는 다음 내용을 포함한다.
  - 구현 결과
  - 주요 변경 파일
  - 실행한 테스트와 빌드 명령
  - 실패, 생략한 검사, 필요한 외부 서비스
  - 요청받아 푸시했다면 커밋 해시와 대상 브랜치
