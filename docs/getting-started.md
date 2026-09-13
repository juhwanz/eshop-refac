# 로컬 실행 가이드

이 문서는 E-Shop을 로컬 프로필로 실행하기 위한 환경변수, MariaDB와 Redis 준비 절차를 설명합니다. 기본 개발 경로는 호스트 MariaDB와 `docker-compose.dev.yml`의 Redis를 사용합니다.

## 요구사항

- Java 21
- 로컬 MariaDB 11.8 서버와 `mariadb` 클라이언트
- Docker와 Docker Compose

## 환경변수 준비

예시 파일을 복사해 Git에서 추적하지 않는 `.env`를 만듭니다.

```bash
cp .env.example .env
```

JWT 서명키는 256비트 이상의 무작위 값을 Base64로 준비합니다.

```bash
openssl rand -base64 32
```

`.env`에 로컬 환경 값과 생성한 JWT 서명키를 설정합니다. placeholder나 실제 비밀값을 Git에 추가하지 않습니다.

```dotenv
DB_USERNAME=eshop
DB_PASSWORD=change-me
DB_PORT=3306
REDIS_PORT=6380
JWT_SECRET_KEY=<openssl 명령의 출력값>
```

`DB_ROOT_PASSWORD`와 `RDS_HOST`는 각각 참고용 전체 스택 Compose와 배포 Compose에서 사용하는 값이며 기본 로컬 실행에는 필요하지 않습니다.

## MariaDB 준비

최초 한 번 application database와 사용자를 준비합니다. 아래 SQL의 비밀번호는 `.env`의 `DB_PASSWORD`와 같아야 합니다.

```bash
sudo mariadb
```

```sql
CREATE DATABASE IF NOT EXISTS eshop CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'eshop'@'localhost' IDENTIFIED BY 'change-me';
ALTER USER 'eshop'@'localhost' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON eshop.* TO 'eshop'@'localhost';
FLUSH PRIVILEGES;
exit;
```

로컬 프로필은 `ddl-auto: update`를 사용하므로 애플리케이션 실행 시 엔티티에 필요한 테이블을 생성하거나 갱신합니다. 단, 기존 `products` 테이블의 재고 CHECK는 자동 추가되지 않으므로 [재고 보호 문서](stock-protection.md)의 확인·적용 절차를 따릅니다.

## Redis와 애플리케이션 실행

Docker를 실행한 상태에서 다음 명령으로 애플리케이션을 시작합니다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

Spring Boot가 `docker-compose.dev.yml`의 Redis를 자동으로 시작하고 애플리케이션 종료 시 함께 중지합니다. 기본 호스트 포트는 다른 프로젝트와의 충돌을 피하도록 6380이며 `.env`의 `REDIS_PORT`로 변경할 수 있습니다.

`.env`는 Spring Boot와 Docker Compose가 자동으로 읽습니다. 로컬 MariaDB 데이터는 호스트의 기존 MariaDB 저장공간에 유지됩니다.

## 접속 주소

- API 진입점: <http://localhost:8080> — Swagger UI로 이동
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Health endpoint: <http://localhost:8080/actuator/health> — 상태만 공개하고 상세 정보는 숨김

## 관련 문서

- [API 안내](api.md)
- [자격 증명 관리와 유출 대응](security/credential-management.md)
- [이미지 게시와 참고용 배포](deployment.md)
