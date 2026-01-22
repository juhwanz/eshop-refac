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
     * Legacy Implementation: Offset Pagination
     * <p>
     * Deep Pagination 발생 시 'Read and Drop' 방식으로 인한 성능 저하를 확인하기 위해 유지함.
     * (No-Offset 구현체와 성능 비교용 Benchmark 대조군)
     * </p>
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

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    /**
     * Optimized Implementation: No-Offset Pagination (Cursor-based)
     * <p>
     * Clustered Index(PK)를 활용하여 스캔 범위를 최소화함 (WHERE id < lastId).
     * 대용량 데이터 조회 시 Offset 방식 대비 약 10배 이상의 성능 개선 확인.
     * </p>
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

    // 동적 쿼리 조건들
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
