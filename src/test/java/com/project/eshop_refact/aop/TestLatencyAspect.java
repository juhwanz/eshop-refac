package com.project.eshop_refact.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Profile("test") // 운영 서버 .yml에 test.simulation.delay-ms=1000이면 서비스 1초 씩 멈춤 방지
public class TestLatencyAspect {
    @Value("${test.simulation.delay-ms:0}")
    private int simulationDelay;

    // ProductService의 decreaseStock으로 시작하는 모든 메서드에 적용
    @Around("execution(* com.project.eshop_refact.service.ProductService.decreaseStock*(..))")
    // 메서드 실행 전 (@Before)나 후 (After)가 아니라, 전체 감싸는 Around
    // -> joinPoint.preoceed() 호출 전. Thread,sleep -> TX 시작 상태로 시간 끓기 -> DB 커넥션과 락 점유 길게.
    // 와일드 카드 (*)를 써서 DB락과 Redis락 한 번에 잡음.
    public Object applyLatency(ProceedingJoinPoint joinPoint) throws Throwable {
        if (simulationDelay > 0) {
            try {
                Thread.sleep(simulationDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return joinPoint.proceed(); // 원래 메서드 실행
    }

}

// Thread.sleep를 서비스 코드에 넣으면 되는데[ 굳이 AOP?
//  1. OCP(개방-패쇄) 준수 -> 테스트를 위해 운영 코드 조작? 에바
//  2. 관심사 분리 -> 지연 시물레이션은 부가 기능
// 이 레이턴시 Tx 밖/안??
// 스프링 AOP의 순서에 따라 다르지만, 보통 @Transactional 어드바이스와 함께 적용됩니다. 저는 이 지연을 통해 DB 락이나 커넥션을 점유하고 있는 시간(Critical Section)을 늘려 동시성 이슈를 더 확실하게 재현하려고 의도했습니다. 따라서 트랜잭션 내부에서 수행되도록 하여, 실제 트래픽이 몰렸을 때 DB가 락을 오래 잡고 있는 상황을 시뮬레이션했습니다."
