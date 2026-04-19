package com.project.eshop_refact.domain.order;

import com.project.eshop_refact.domain.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * 사용자별 최신 주문 목록 조회
     */
    List<Order> findAllByUserOrderByIdDesc(User user);

    Page<Order> findAllByUser(User user, Pageable pageable);

    /**
     * (Legacy) Fetch Join을 활용한 사용자 주문 상세 조회
     * N+1 문제를 방지하기 위해 연관된 OrderItem과 Product를 한 번에 조회하며,
     * 1:N 조인으로 인해 발생하는 데이터 중복(Cartesian Product)은 distinct로 제거합니다.
     */
    @Query("select distinct o from Order o " +
            "join fetch o.orderItems oi " +
            "join fetch oi.product p " +
            "where o.user = :user " +
            "order by o.id desc")
    List<Order> findAllByUserWithFetchJoin(@Param("user") User user);

    /**
     * (Legacy) 페이징을 지원하는 Fetch Join 사용자 주문 조회
     * 페이징 성능 최적화를 위해 카운트 쿼리(countQuery)를 분리하여 불필요한 조인 연산을 제거했습니다.
     */
    @Query(value = "select o from Order o join fetch o.orderItems where o.user = :user",
            countQuery = "select count(o) from Order o where o.user = :user")
    Page<Order> findAllByUserWithFetchJoinAndPaging(@Param("user") User user, Pageable pageable);
}
