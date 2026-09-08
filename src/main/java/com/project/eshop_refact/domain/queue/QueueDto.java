package com.project.eshop_refact.domain.queue;

import lombok.AllArgsConstructor;
import lombok.Getter;

public final class QueueDto {

    private QueueDto() {
    }

    @Getter
    @AllArgsConstructor
    public static class Response {
        private QueueStatus status;
        private Long rank;
    }
}
