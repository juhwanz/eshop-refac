package com.project.eshop_refact.repository;

import com.project.eshop_refact.domain.Product;
import com.project.eshop_refact.dto.ProductDto;
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

import static com.project.eshop_refact.domain.QProduct.product;

@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * Legacy Strategy : Off-Set 페이지네이션
     * - Deep Pagination 시 'Read and Drop' 방식 -> 성능 저하 O(N)
     * - 관리자 페이지 등 특정 페이지 이동이 필수적인 요구사항을 위해 유지.
     *
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

        // 페이지 사이즈보다 컨텐츠가 적을 경우 Count Query 생략.
        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    /**
     * Performance Tuning : No - OffSet 페이지 네이션
     * - Clusterd Index(PK)를 활용해 스캔 범위 최소화 (Where id < lastId)
     * - Count Query 제거 -> 대용량 데이터 조회 시 일정한 응답 속도 (O(1)) 보장.
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
                .limit(pageable.getPageSize() + 1) // 다음 페이지 있는지 확인하려고 +1 조회
                .fetch();

        boolean hasNext = false;
        if (content.size() > pageable.getPageSize()) {
            content.remove(pageable.getPageSize());
            hasNext = true;
        }

        return new SliceImpl<>(content, pageable, hasNext);
    }

    // Dynamic Query: BooleanExpression을 활용한 조건절 모듈화 및 재사용성 증대
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
