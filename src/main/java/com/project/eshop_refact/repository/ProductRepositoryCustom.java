package com.project.eshop_refact.repository;

import com.project.eshop_refact.domain.Product;
import com.project.eshop_refact.dto.ProductDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;

public interface ProductRepositoryCustom {
    // 기존 (off-set 방식) -  앞에서부터 다 읽고 버리는 방식
    Page<Product> search(ProductDto.SearchCondition condition, Pageable pageable);

    // No-off-set 방식 - 마지막으로 조회한 ID보다 작은 것 부터 바로 시작
    // Page 대신 Slice 반환
    Slice<Product> searchNoOffset(Long lastProductId, ProductDto.SearchCondition condition, Pageable pageable);
}

