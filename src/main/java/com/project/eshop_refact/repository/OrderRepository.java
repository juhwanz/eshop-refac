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
    List<Order> findAllByUserOrderByIdDesc(User user);
    Page<Order> findAllByUser(User user, Pageable pageable);

    // Fetch join
    @Query("select distinct o from Order o " +
            "join fetch o.orderItems oi " +
            "join fetch oi.product p " +
            "where o.user = :user " +
            "order by o.id desc")
    List<Order> findAllByUserWithFetchJoin(@Param("user") User user);

    // Fetch join Test (+ paging)
    @Query(value = "select o from Order o join fetch o.orderItems where o.user = :user",
            countQuery = "select count(o) from Order o where o.user = :user")
    Page<Order> findAllByUserWithFetchJoinAndPaging(@Param("user") User user, Pageable pageable);
}
