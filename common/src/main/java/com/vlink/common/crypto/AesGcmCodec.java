package com.vlink.common.crypto;

import java.security.GeneralSecurityException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

// AesGcmCodec 封装 AES-GCM 加解密，包含 AAD 与认证标签校验。

public final class AesGcmCodec {
    private static final int TAG_BITS = 128;

    private AesGcmCodec() {
    }

    public static byte[] encrypt(byte[] key, byte[] nonce, byte[] aad, byte[] plaintext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_BITS, nonce);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
            cipher.updateAAD(aad);
            return cipher.doFinal(plaintext);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("AES-GCM encrypt failed", ex);
        }
    }

    public static byte[] decrypt(byte[] key, byte[] nonce, byte[] aad, byte[] ciphertext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_BITS, nonce);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
            cipher.updateAAD(aad);
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("AES-GCM decrypt failed", ex);
        }
    }
}

