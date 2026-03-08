package com.project.eshop_refact.repository;

import com.project.eshop_refact.domain.Order;
import com.project.eshop_refact.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // Indexing: ID 역순 정렬을 통해 최신 주문 우선 조회 (No-Offset Paging 기반)
    List<Order> findAllByUserOrderByIdDesc(User user);

    Page<Order> findAllByUser(User user, Pageable pageable);

    // 레거시
    // Performance: N+1 문제 해결을 위한 Fetch Join 적용 (Order -> OrderItem -> Product)
    // Distinct: 1:N 조인 시 발생하는 데이터 중복(Cartesian Product) 제거
    @Query("select distinct o from Order o " +
            "join fetch o.orderItems oi " +
            "join fetch oi.product p " +
            "where o.user = :user " +
            "order by o.id desc")
    List<Order> findAllByUserWithFetchJoin(@Param("user") User user);

    // 레거시/
    // Optimization: Count Query 분리를 통해 불필요한 Join 연산 제거 및 페이징 성능 확보
    @Query(value = "select o from Order o join fetch o.orderItems where o.user = :user",
            countQuery = "select count(o) from Order o where o.user = :user")
    Page<Order> findAllByUserWithFetchJoinAndPaging(@Param("user") User user, Pageable pageable);
}
