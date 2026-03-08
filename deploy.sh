#!/bin/bash

# 1. 현재 실행 중인 교대 근무자(컨테이너) 확인
IS_GREEN=$(sudo docker ps | grep eshop-green)

if [ -z "$IS_GREEN" ]; then
    echo "### BLUE => GREEN 배포 시작 ###"
    TARGET_UP="eshop-green"
    TARGET_DOWN="eshop-blue"
    TARGET_PORT=8081
else
    echo "### GREEN => BLUE 배포 시작 ###"
    TARGET_UP="eshop-blue"
    TARGET_DOWN="eshop-green"
    TARGET_PORT=8080
fi

# 2. 최신 도커 이미지 Pull 및 새 컨테이너 실행 (운영 파일 사용)
echo "1. 최신 도커 이미지 Pull"
sudo docker-compose -f docker-compose.prod.yml pull $TARGET_UP

echo "2. 새 컨테이너($TARGET_UP) 실행"
sudo docker-compose -f docker-compose.prod.yml up -d $TARGET_UP

# 3. 스프링 부트가 완전히 켜질 때까지 안전하게 대기 (Health Check 대용)
echo "3. 15초 대기 (스프링 부트 부팅 시간)..."
sleep 15

# 4. Nginx가 바라보는 포트 스위칭 (동적 라우팅)
echo "4. Nginx 프록시 포트를 $TARGET_PORT 로 스위칭"
echo "set \$service_url http://127.0.0.1:$TARGET_PORT;" | sudo tee /etc/nginx/conf.d/service-url.inc

echo "5. Nginx 리로드 (단 0.1초의 멈춤 없이 트래픽 전환)"
sudo systemctl reload nginx

# 5. 임무를 마친 구형 컨테이너 종료 및 삭제
echo "6. 기존 컨테이너($TARGET_DOWN) 종료"
sudo docker-compose -f docker-compose.prod.yml stop $TARGET_DOWN
sudo docker-compose -f docker-compose.prod.yml rm -f $TARGET_DOWN

echo "### 무중단 배포 완벽하게 성공 ###"