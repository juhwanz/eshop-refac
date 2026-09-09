package com.project.eshop_refact.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

/**
 * 상품 검색 커스텀 레포지토리
 * QueryDSL을 활용한 동적 검색과 두 가지 페이징 방식을 제공합니다.
 */
public interface ProductRepositoryCustom {

    /**
     * 상품 동적 검색 (Offset 기반 페이징)
     * 전통적인 게시판 형태의 페이지네이션을 지원하며, 전체 데이터 건수(Count)를 포함한 결과를 반환합니다.
     */
    Page<Product> search(ProductDto.SearchCondition condition, Pageable pageable);

    /**
     * 상품 무한 스크롤 검색 (No-Offset / Keyset 기반 페이징)
     * 마지막으로 조회된 상품 ID(lastProductId)를 기준으로 다음 데이터를 조회합니다.
     * Offset과 전체 개수 조회 없이 다음 구간을 반환합니다.
     */
    Slice<Product> searchNoOffset(Long lastProductId, ProductDto.SearchCondition condition, Pageable pageable);
}
