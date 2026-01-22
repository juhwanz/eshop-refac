package com.project.eshop_refact.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TestLatencyAspect {
    @Value("${test.simulation.delay-ms:0}")
    private int simulationDelay;

    // ProductService의 decreaseStock으로 시작하는 모든 메서드에 적용
    @Around("execution(* com.project.eshop_refact.service.ProductService.decreaseStock*(..))")
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
