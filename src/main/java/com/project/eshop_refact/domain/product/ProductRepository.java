package com.project.eshop_refact.domain.product;


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

    /**
     * 비관적 락(Pessimistic Lock)을 적용한 상품 단건 조회
     * 동시성 환경에서 재고 수정 시 발생할 수 있는 데이터 정합성 문제를 방지합니다.
     * 데드락(Deadlock) 및 스레드 무한 대기를 방지하기 위해 락 획득 최대 대기 시간을 10초로 제한합니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000")})
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdWithPessimisticLock(@Param("id") Long id);
}
