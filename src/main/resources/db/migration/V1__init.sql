-- V1__init.sql: e-commerce 핵심 도메인(회원, 상품, 주문) 최초 스키마 세팅

-- 회원 정보 및 인증/인가 상태 관리를 위한 테이블
-- 계정 보호 정책(login_fail_count) 및 계정 활성화 상태(status)를 포함합니다.
CREATE TABLE IF NOT EXISTS users (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    username VARCHAR(100) NOT NULL,
    role VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    login_fail_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6),
    updated_at DATETIME(6)
    );

-- 상품 기본 정보 및 재고 관리를 위한 테이블
CREATE TABLE IF NOT EXISTS products (
                                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                        name VARCHAR(255) NOT NULL,
    price INT NOT NULL,
    stock_quantity INT NOT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6)
    );

-- 회원의 주문 라이프사이클(상태)을 관리하는 테이블
CREATE TABLE IF NOT EXISTS orders (
                                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                      user_id BIGINT NOT NULL,
                                      status VARCHAR(50) NOT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6)
    );

-- 주문 상세 내역 및 상품 가격 스냅샷 테이블
-- 향후 상품의 원가격(products.price)이 변동되더라도 기존 결제 내역에 영향을 주지 않도록,
-- 주문 시점의 체결 가격(order_price)을 독립적으로 저장합니다.
CREATE TABLE IF NOT EXISTS order_item (
                                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                          order_id BIGINT NOT NULL,
                                          product_id BIGINT NOT NULL,
                                          order_price INT NOT NULL,
                                          count INT NOT NULL,
                                          created_at DATETIME(6),
    updated_at DATETIME(6)
    );