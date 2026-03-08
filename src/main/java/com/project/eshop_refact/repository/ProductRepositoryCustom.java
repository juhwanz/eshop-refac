package com.project.eshop_refact.repository;

import com.project.eshop_refact.domain.Product;
import com.project.eshop_refact.dto.ProductDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface ProductRepositoryCustom {
    // 기존 (off-set 방식) -  앞에서부터 다 읽고 버리는 방식 (Count Query 비용 발생, 깊은 페이지 조회 시 성능 저하)
    Page<Product> search(ProductDto.SearchCondition condition, Pageable pageable);

    // Optimization: 대용량 데이터 조회 성능 개선을 위한 No-Offset (Keyset) 페이징
    // Infinite Scroll: 다음 페이지 존재 여부만 확인(Slice)하여 불필요한 Count 연산 제거
    Slice<Product> searchNoOffset(Long lastProductId, ProductDto.SearchCondition condition, Pageable pageable);
}

