package com.project.eshop_refact.service;

import com.project.eshop_refact.domain.order.OrderService;
import com.project.eshop_refact.domain.order.RedissonLockStockFacade;
import com.project.eshop_refact.domain.queue.WaitingQueueService;
import com.project.eshop_refact.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedissonLockStockFacadeTest {
    @Mock RedissonClient client;
    @Mock RLock lock;
    @Mock OrderService orders;
    @Mock WaitingQueueService queue;
    RedissonLockStockFacade facade;

    @BeforeEach
    void setUp() {
        facade = new RedissonLockStockFacade(client, orders, queue);
        ReflectionTestUtils.setField(facade, "waitTime", 10L);
        when(client.getLock("product:stock:2")).thenReturn(lock);
    }

    @Test
    void successUnlocksAfterServiceAndConsumesPermission() throws Exception {
        when(lock.tryLock(10, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(orders.order(1L, 2L, 1)).thenReturn(3L);
        assertThat(facade.order(1L, 2L, 1)).isEqualTo(3L);
        var sequence = inOrder(lock, orders, queue);
        sequence.verify(lock).tryLock(10, TimeUnit.SECONDS);
        sequence.verify(orders).order(1L, 2L, 1);
        sequence.verify(lock).isHeldByCurrentThread();
        sequence.verify(lock).unlock();
        sequence.verify(queue).removeUser(1L);
    }

    @Test
    void failurePreservesPermission() throws Exception {
        when(lock.tryLock(10, TimeUnit.SECONDS)).thenReturn(false);
        assertThatThrownBy(() -> facade.order(1L, 2L, 1)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(orders, queue);
        verify(lock, never()).unlock();
    }

    @Test
    void interruptionRestoresFlagAndPreservesPermission() throws Exception {
        when(lock.tryLock(10, TimeUnit.SECONDS)).thenThrow(new InterruptedException());
        try {
            assertThatThrownBy(() -> facade.order(1L, 2L, 1)).isInstanceOf(IllegalStateException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verifyNoInteractions(orders, queue);
            verify(lock, never()).unlock();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void serviceFailureStillCleansUpWithoutUnlockingAnotherOwner() throws Exception {
        when(lock.tryLock(10, TimeUnit.SECONDS)).thenReturn(true);
        var failure = new IllegalStateException("rollback");
        when(orders.order(1L, 2L, 1)).thenThrow(failure);
        assertThatThrownBy(() -> facade.order(1L, 2L, 1)).isSameAs(failure);
        verify(lock, never()).unlock();
        verify(queue).removeUser(1L);
    }

    @Test
    void cancellationFailurePreservesPermission() throws Exception {
        when(orders.getProductIdByOrderId(3L)).thenReturn(2L);
        when(lock.tryLock(10, TimeUnit.SECONDS)).thenReturn(false);
        assertThatThrownBy(() -> facade.cancelOrder(3L, 1L)).isInstanceOf(BusinessException.class);
        verify(orders, never()).cancelOrder(3L, 1L);
        verify(lock, never()).unlock();
        verifyNoInteractions(queue);
    }

    @Test
    void cancellationInterruptionRestoresFlag() throws Exception {
        when(orders.getProductIdByOrderId(3L)).thenReturn(2L);
        when(lock.tryLock(10, TimeUnit.SECONDS)).thenThrow(new InterruptedException());
        try {
            assertThatThrownBy(() -> facade.cancelOrder(3L, 1L)).isInstanceOf(IllegalStateException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(orders, never()).cancelOrder(3L, 1L);
            verify(lock, never()).unlock();
            verifyNoInteractions(queue);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void cancellationExceptionReleasesOwnedLock() throws Exception {
        when(orders.getProductIdByOrderId(3L)).thenReturn(2L);
        when(lock.tryLock(10, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        var failure = new IllegalStateException("rollback");
        doThrow(failure).when(orders).cancelOrder(3L, 1L);
        assertThatThrownBy(() -> facade.cancelOrder(3L, 1L)).isSameAs(failure);
        verify(lock).unlock();
        verifyNoInteractions(queue);
    }

    @Test
    void cancellationUsesWatchdogAndDoesNotConsumePermission() throws Exception {
        when(orders.getProductIdByOrderId(3L)).thenReturn(2L);
        when(lock.tryLock(10, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        facade.cancelOrder(3L, 1L);
        verify(orders).cancelOrder(3L, 1L);
        verify(lock).unlock();
        verifyNoInteractions(queue);
    }
}
