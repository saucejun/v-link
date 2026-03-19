package com.vlink.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

// PskKey 负责把命令行 PSK 派生为固定长度的 AES 密钥。

public final class PskKey {
    private PskKey() {
    }

    public static byte[] deriveAes128Key(String psk) {
        if (psk == null || psk.isEmpty()) {
            throw new IllegalArgumentException("PSK must not be empty");
        }
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha256.digest(psk.getBytes(StandardCharsets.UTF_8));
            return Arrays.copyOf(digest, 16);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}

