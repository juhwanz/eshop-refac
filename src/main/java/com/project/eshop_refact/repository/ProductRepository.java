package com.project.eshop_refact.repository;


import com.project.eshop_refact.domain.Product;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, ProductRepositoryCustom {

    // 동시성 제어 : 재고 수정 시 Race Condition 방지를 위해 비관적 락 (Select .. for Update) 적용
    // Stability : 락 흭득 시간을 설정해 무한 대기 방지 및 Fail - Fast 구현
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    //DB 설정이 씹혀도, JPA 레벨에서 강제로 10초(10000ms) 대기를 명령합니다.
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000")})
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdWithPessimisticLock(@Param("id") Long id);
}
