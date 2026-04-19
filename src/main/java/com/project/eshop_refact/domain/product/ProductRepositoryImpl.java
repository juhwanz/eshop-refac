package com.project.eshop_refact.domain.product;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.List;

import static com.project.eshop_refact.domain.product.QProduct.product;

@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * 상품 동적 검색 (Offset 기반 페이징)
     * 특정 페이지로의 직접 이동이 필수적인 관리자 페이지 등의 요구사항을 위해 사용합니다.
     */
    @Override
    public Page<Product> search(ProductDto.SearchCondition condition, Pageable pageable) {
        List<Product> content = queryFactory
                .selectFrom(product)
                .where(
                        nameContains(condition.getName()),
                        priceBetween(condition.getMinPrice(), condition.getMaxPrice())
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(product.count())
                .from(product)
                .where(
                        nameContains(condition.getName()),
                        priceBetween(condition.getMinPrice(), condition.getMaxPrice())
                );

        // 조회된 데이터가 페이지 사이즈보다 적을 경우, 불필요한 Count 쿼리를 생략하여 성능을 최적화합니다.
        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    /**
     * 상품 무한 스크롤 검색 (No-Offset 기반 페이징)
     * 클러스터링 인덱스(PK)를 활용해 스캔 범위를 최소화하여 대용량 데이터 조회 시 일정한 응답 속도를 보장합니다.
     */
    @Override
    public Slice<Product> searchNoOffset(Long lastProductId, ProductDto.SearchCondition condition, Pageable pageable) {
        List<Product> content = queryFactory
                .selectFrom(product)
                .where(
                        ltProductId(lastProductId),
                        nameContains(condition.getName()),
                        priceBetween(condition.getMinPrice(), condition.getMaxPrice())
                )
                .orderBy(product.id.desc())
                // Slice 구현을 위해 요청된 페이지 사이즈보다 1건 더 조회하여 다음 데이터 존재 여부를 확인합니다.
                .limit(pageable.getPageSize() + 1) // 다음 페이지 있는지 확인하려고 +1 조회
                .fetch();

        boolean hasNext = false;
        if (content.size() > pageable.getPageSize()) {
            content.remove(pageable.getPageSize());
            hasNext = true;
        }

        return new SliceImpl<>(content, pageable, hasNext);
    }

    // --- Dynamic Query BooleanExpressions ---

    private BooleanExpression ltProductId(Long lastProductId) {
        return lastProductId == null ? null : product.id.lt(lastProductId);
    }

    private BooleanExpression nameContains(String name) {
        return name != null ? product.name.contains(name) : null;
    }

    private BooleanExpression priceBetween(Integer minPrice, Integer maxPrice) {
        if (minPrice == null && maxPrice == null) return null;
        if (minPrice != null && maxPrice != null) return product.price.between(minPrice, maxPrice);
        if (minPrice != null) return product.price.goe(minPrice);
        return product.price.loe(maxPrice);
    }
}
