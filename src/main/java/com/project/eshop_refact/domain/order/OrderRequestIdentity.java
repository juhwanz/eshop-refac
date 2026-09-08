package com.project.eshop_refact.domain.order;

import com.project.eshop_refact.global.exception.BusinessException;
import com.project.eshop_refact.global.exception.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record OrderRequestIdentity(String key, String fingerprint) {

    public static OrderRequestIdentity of(String key, Long productId, int count) {
        if (key == null || !key.matches("[!-~]{1,128}") || productId == null || count < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        try {
            byte[] payload = ("v1:" + productId + ":" + count).getBytes(StandardCharsets.UTF_8);
            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
            return new OrderRequestIdentity(key, fingerprint);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    public byte[] keyBytes() {
        return key.getBytes(StandardCharsets.US_ASCII);
    }

    public void verifyFingerprint(String stored) {
        if (!fingerprint.equals(stored)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
    }
}
