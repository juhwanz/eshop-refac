#!/bin/bash

# 참고용 수동 배포 스크립트입니다. 현재 GitHub Actions나 운영 서버에 연결되어 있지 않습니다.
# 고정 대기 후 전환하므로 애플리케이션 readiness, 무중단 전환, 실패 시 rollback을 보장하지 않습니다.

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

# 3. 참고용 고정 대기 (readiness 확인이 아님)
echo "3. 15초 고정 대기 (readiness 확인 없음)..."
sleep 15

# 4. Nginx가 바라보는 포트 스위칭 (동적 라우팅)
echo "4. Nginx 프록시 포트를 $TARGET_PORT 로 스위칭"
echo "set \$service_url http://127.0.0.1:$TARGET_PORT;" | sudo tee /etc/nginx/conf.d/service-url.inc

echo "5. Nginx 리로드"
sudo systemctl reload nginx

# 5. 임무를 마친 구형 컨테이너 종료 및 삭제
echo "6. 기존 컨테이너($TARGET_DOWN) 종료"
sudo docker-compose -f docker-compose.prod.yml stop $TARGET_DOWN
sudo docker-compose -f docker-compose.prod.yml rm -f $TARGET_DOWN

echo "### 참고용 배포 절차 완료 ###"
