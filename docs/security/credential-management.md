# 자격 증명 관리와 유출 대응

## 기본 원칙

- 실제 비밀번호, JWT 서명키, 토큰, SSH 개인키는 Git, 이슈, PR, 로그에 기록하지 않는다.
- 저장소에는 placeholder가 있는 예시 파일만 두고 실제 값은 환경변수나 배포 플랫폼의 secret으로 주입한다.
- 운영 자격 증명은 서비스별로 분리하고 필요한 최소 권한만 부여한다.
- 유출이 의심되면 Git 이력 정리보다 먼저 발급처에서 해당 자격 증명을 폐기하거나 교체한다.

## 로컬 실행

`.env.example`을 참고해 저장소에서 추적되지 않는 `.env`를 만들거나 셸 환경변수를 설정한다.

필수 값은 다음과 같다.

- `DB_USERNAME`: 데이터베이스 사용자
- `DB_PASSWORD`: 데이터베이스 비밀번호
- `JWT_SECRET_KEY`: Base64로 인코딩된 256비트 이상의 무작위 JWT 서명키
- `RDS_HOST`: 운영 Compose를 사용할 때의 데이터베이스 호스트

`.env`와 `src/main/resources/application-secret.yaml`은 Git에 추가하지 않는다. 실제 값을 예시 파일에 복사하지 않는다. 공유 시스템에서는 `chmod 600 .env`로 파일을 현재 사용자만 읽고 쓸 수 있게 제한한다.

## 운영 배포

- 운영 DB에는 관리자 계정 대신 애플리케이션 전용 계정을 사용한다.
- EC2의 `.env`는 배포 사용자만 읽을 수 있도록 권한을 제한한다.
- Docker Hub access token과 EC2 SSH key는 GitHub Actions secret으로 관리한다.
- 자격 증명을 교체할 때는 새 값 배포와 상태 확인을 마친 뒤 기존 값을 폐기한다.
- `prod` 프로필은 `DB_PASSWORD`와 `JWT_SECRET_KEY`가 없거나 공백이면 명확한 오류와 함께 기동을 중단한다.

## 운영 endpoint

- 외부에는 `/actuator/health`만 공개하고 응답의 상세 구성 요소는 숨긴다.
- `/actuator/metrics`를 포함한 나머지 Actuator endpoint는 노출하지 않고 Spring Security에서도 차단한다.
- 운영 모니터링을 확장할 때는 endpoint를 공개하기 전에 별도의 인증 또는 내부망 접근 정책을 먼저 적용한다.

## JWT 서명키 교체

JWT 서명키가 유출되면 새 키를 배포한 뒤 Redis의 `RT:*` 키만 제거한다. 새 키로 배포된 서버는 기존 access token의 서명을 거부하고, `RT:*` 제거로 기존 refresh token의 재사용도 막는다.

Redis 전체를 비우면 캐시, 주문 멱등성, 대기열 상태까지 손실되므로 `FLUSHDB`를 사용하지 않는다.

## 유출 대응 절차

1. 노출 가능성이 있는 자격 증명의 종류, 발급처, 소유자, 권한과 사용처를 실제 값 없이 기록한다.
2. 새 자격 증명을 발급하고 애플리케이션과 배포 시스템에 적용한다.
3. 상태 확인과 최소 기능 검증 후 기존 자격 증명을 폐기한다.
4. Git 이력을 재작성하고 모든 공개 브랜치와 태그를 갱신한다.
5. 새 clone에서 전체 Git 이력을 secret scanner로 검사한다.
6. PR ref나 캐시가 남으면 GitHub Support에 제거를 요청한다.
7. 협업자는 기존 clone을 폐기하고 새로 clone한다. 오래된 브랜치를 push하지 않는다.

## 검사

GitHub secret scanning과 push protection을 활성화한다. CI의 Gitleaks 검사를 함께 사용해 provider token뿐 아니라 JWT 형태 값도 차단한다.

검사 결과나 실패 로그에는 실제 비밀값이 노출되지 않도록 redaction을 유지한다.

## 이슈 #8 잔여 위험 기록

2026년 9월 4일 공개 브랜치의 Git 이력을 재작성하고 전체 이력을 다시 검사했다. 당시 사용하던 EC2와 RDS는 삭제되어 관련 자격 증명을 더 이상 인증에 사용할 수 없다.

GitHub가 관리하는 과거 PR #1, #2, #3, #4, #7의 읽기 전용 ref에는 재작성 전 커밋이 남아 있다. 개인 프로젝트의 제한된 영향과 이미 무효화된 자격 증명을 고려해 이 잔여 위험을 수용한다. 해당 ref를 완전히 제거하려면 GitHub Support의 서버 측 purge가 필요하다.

재작성 전 clone이나 브랜치를 다시 push하면 제거한 이력이 복원될 수 있으므로 사용하지 않는다.
