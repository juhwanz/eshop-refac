# Spring 기본기 학습 범위

E-Shop 구현을 설명하기 전에 Spring이 객체를 만들고 요청과 횡단 관심사를 처리하는 기본 원리를 학습한다.

## 학습 주제

- IoC와 DI, 생성자 주입
- Bean 등록, scope와 생명주기
- singleton Bean과 thread safety
- `@SpringBootApplication`, component scan과 auto-configuration
- profile, 외부 설정과 type-safe configuration binding
- AOP와 proxy
- `@Transactional` 적용 조건과 self-invocation
- DispatcherServlet과 Spring MVC 요청 흐름
- filter, interceptor와 argument resolver
- Spring Security filter chain
- CORS, CSRF와 stateless API
- validation, DTO, controller advice와 공통 예외 응답

각 주제는 일반 원리를 먼저 설명하고, E-Shop의 실제 component와 요청 흐름에 연결한다. 프로젝트에서 사용하지 않은 기능을 구현 경험으로 표현하지 않는다.
