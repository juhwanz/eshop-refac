package com.project.eshop_refact.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 동시성 테스트용 지연(Latency) 주입 AOP
 * 실제 트래픽이 몰렸을 때의 병목 상황을 시뮬레이션하기 위해, 의도적인 지연을 발생시켜
 * DB 커넥션 및 락(Lock) 점유 시간(Critical Section)을 늘립니다.
 * 운영 환경에 영향을 주지 않도록 'test' 프로파일에서만 격리되어 활성화됩니다.
 */
@Aspect
@Component
@Profile("test")
public class TestLatencyAspect {
    @Value("${test.simulation.delay-ms:0}")
    private int simulationDelay;

    /**
     * ProductService의 모든 재고 차감 메서드(DB 락, Redis 락 적용 등) 실행 직전에 지연을 주입합니다.
     */
    @Around("execution(* com.project.eshop_refact.domain.product.ProductService.decreaseStock*(..))")
    public Object applyLatency(ProceedingJoinPoint joinPoint) throws Throwable {
        if (simulationDelay > 0) {
            try {
                Thread.sleep(simulationDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return joinPoint.proceed();
    }

}
