# High-Traffic E-Commerce Backend Refactoring

> **대용량 트래픽과 데이터 환경에서의 시스템 안정성 및 성능 최적화 프로젝트**

[![Java](https://img.shields.io/badge/Java-17-orange?logo=java)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.0-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue?logo=mysql)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-Redisson-red?logo=redis)](https://redis.io/)
[![QueryDSL](https://img.shields.io/badge/QueryDSL-5.1.0-lightgrey)](http://querydsl.com/)

## 📝 Project Overview

단순한 기능 구현을 넘어, **데이터 정합성(Consistency)** 확보와 **시스템 가용성(Availability)** 유지에 집중한 백엔드 프로젝트입니다.
선착순 이벤트와 같은 고트래픽 상황에서의 **장애 전파(Cascading Failure) 차단**, **대용량 데이터 조회 성능 개선**, 그리고 **안정적인 캐싱 전략** 수립 과정을 통해 엔지니어링 역량을 증명했습니다.

## 🛠 Tech Stack

| Category | Technology | Version | Description |
|---|---|---|---|
| **Language** | Java | 17 (LTS) |  |
| **Framework** | Spring Boot | 3.3.0 |  |
| **Database** | MySQL | 8.0 | Main DBMS |
| **ORM** | Spring Data JPA | - | with QueryDSL 5.1 |
| **Cache / Lock** | Redis (Redisson) | 3.31.0 | Distributed Lock & Caching |
| **Security** | Spring Security | 6.x | JWT Authentication |
| **Test** | JUnit5 / Mockito | - | Unit & Integration Test |

## 🏗 System Architecture

데이터 정합성과 성능의 균형을 맞추기 위해 **Facade Pattern**을 적용했습니다.
트랜잭션(`@Transactional`) 범위 밖에서 락의 획득/해제를 제어함으로써, 락 점유 대기 중 DB 커넥션이 고갈되는 문제를 원천 차단했습니다.

```mermaid
graph TD
    Client[Client] -->|POST /orders| Controller[Order Controller]
    Controller -->|Request| Facade[Redisson Lock Facade]
    
    subgraph "Transaction Boundary Strategy"
    Facade -->|1. Try Lock (Pub/Sub)| Redis[(Redis)]
    Facade -->|2. Transaction Start| Service[Order Service]
    Service -->|3. Decrease Stock| DB[(MySQL)]
    Service -->|4. Commit / Rollback| DB
    Facade -->|5. Unlock| Redis
    end

🔥 Key Technical Challenges & Solutions
1. 동시성 제어: DB 커넥션 고갈 및 장애 전파 해결
문제 상황: 주문 트래픽 폭주 시 DB 비관적 락(Pessimistic Lock) 대기열이 길어지면서 DB 커넥션 풀(Pool)이 고갈됨. 이로 인해 주문과 무관한 단순 조회 API까지 타임아웃이 발생하는 '장애 전파' 현상 확인.

해결 전략:
전략 (Strategy),방식,장점,단점,결과
DB Pessimistic Lock,select for update,강력한 정합성,커넥션 고갈로 전체 서비스 마비,⚠️ 기각
Redis Distributed Lock,Redisson (Pub/Sub),"대기열을 Redis로 이관, DB 보호",인프라 복잡도 증가,✅ 채택
📉 성과 (Availability):

주문 로직이 지연되더라도 **조회 API 성공률 0% → 100%**로 가용성 회복.

처리량(Throughput) 약 3.6배 향상 (Avg Latency: 154ms → 42ms).

2. 대용량 조회 최적화: Deep Pagination
문제 상황: 상품 데이터 50만 건 이상 적재 시, 기존 OFFSET 페이징(LIMIT 500000, 10) 방식은 DB가 앞부분 데이터를 읽고 버리는(Scan and Drop) 과정에서 O(N)의 성능 저하 발생.

해결 방안:

No-Offset (Cursor-based) 도입: WHERE id < lastId 조건을 사용하여 인덱스(Index)를 바로 타도록 쿼리 튜닝.

QueryDSL 활용: 동적 쿼리 및 Type-Safe한 구현.

📉 성과 (Latency):

Legacy Offset Paging: 48ms (페이지 깊을수록 느려짐)

No-Offset Paging: 4ms (데이터 위치와 무관하게 O(1) 속도 유지) -> 약 12배 성능 개선

3. ORM 최적화: N+1 문제와 메모리 효율성
문제 상황: 주문 목록 조회 시(OneToMany), 연관된 상품 정보를 가져오기 위해 주문 개수(N)만큼 추가 쿼리가 발생하는 N+1 문제 발생 (10건 조회 시 11회 쿼리).

해결 전략:

Fetch Join 미사용: 페이징 쿼리에 Fetch Join 사용 시 **모든 데이터를 메모리에 로딩(In-Memory Paging)**하여 OOM(Out Of Memory) 발생 위험이 있음.

Batch Size 적용 (최종): hibernate.default_batch_fetch_size 설정을 통해 IN 절로 데이터를 묶어서 조회하도록 최적화.

📉 성과 (Query Count):

쿼리 수: 11회 -> 2회 (약 90% 감소)

메모리 사용량 안정화 및 대량 조회 시 확장성 확보.

4. 캐시 전략: 정합성과 성능의 조화
문제 상황: 조회 성능 개선을 위해 Redis 캐싱(Look-aside)을 도입했으나, 관리자가 가격 수정 시 캐시와 DB 간 데이터 불일치(Stale Data) 위험 존재.

해결 방안:

Write Eviction (수정 시 삭제) 전략: 데이터 수정 시 캐시를 갱신(Update)하는 대신 **삭제(@CacheEvict)**하여, 다음 조회 시 DB에서 최신 값을 강제로 읽어오도록 설계. (갱신 로직의 복잡성과 Race Condition 위험 제거)

📉 성과 (Consistency):

가격 수정 직후 조회 시, DB 쿼리가 발생하며 최신 데이터 반영 100% 보장 검증 완료.

🧪 Testing Strategy
Integration Test:

OrderConcurrencyIntegrationTest: ExecutorService (100 Threads)를 활용하여 재고 정합성 및 락 동작 검증.

ProductDeepPaginationTest: JDBC Batch Insert로 대량 데이터 프로비저닝 후 페이징 성능 벤치마크 수행.

🚀 Getting Started
1. Prerequisites

Java 17+, Docker & Docker Compose

2. Setup & Run

Bash

# Infrastructure (MySQL, Redis)
docker-compose up -d

# Build & Run
./gradlew clean build
./gradlew bootRun
3. Environment Variables 보안상 제외된 application-secret.yml 대신 환경 변수 설정이 필요합니다.

Bash

export DB_PASSWORD=your_password
export JWT_SECRET_KEY=your_secret_key
📝 API Documentation
Swagger URL: http://localhost:8080/swagger-ui/index.html