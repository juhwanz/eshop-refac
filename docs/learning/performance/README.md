# 성능 학습과 검증

성능을 단일 응답 시간이나 최고 처리량으로 판단하지 않고, 병목 가설·측정 환경·분포·정합성을 함께 설명하는 것을 목표로 한다.

## 학습 예정 주제

- latency, throughput, concurrency와 saturation
- 평균, p50, p95와 p99
- warm-up, 반복 실행과 비교 가능한 workload
- connection pool과 lock contention
- JPA N+1, batch fetch와 쿼리 수
- Offset, No-Offset와 인덱스
- k6 threshold와 회귀 baseline
- 실험 결과와 운영 SLO의 차이

## 기록 원칙

- 측정일, commit, 환경, 데이터량, VU, duration과 반복 횟수를 기록한다.
- 예상된 비성공과 시스템 오류를 구분한다.
- 재고 같은 비즈니스 불변조건을 성능 지표와 함께 확인한다.
- 비교 대상은 가능한 한 동일한 로직과 조건을 사용한다.
- 특정 로컬 결과를 운영 용량이나 보편적인 성능 우위로 표현하지 않는다.

현재 프로젝트의 검증된 결과는 [`../../load-testing.md`](../../load-testing.md)를 기준으로 한다.
