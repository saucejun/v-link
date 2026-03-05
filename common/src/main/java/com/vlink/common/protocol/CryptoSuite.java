package com.vlink.common.protocol;

public enum CryptoSuite {
    AES_128_GCM((byte) 1);

    private final byte code;

    CryptoSuite(byte code) {
        this.code = code;
    }

    public byte code() {
        return code;
    }

    public static CryptoSuite fromCode(byte code) {
        for (CryptoSuite suite : values()) {
            if (suite.code == code) {
                return suite;
            }
        }
        throw new IllegalArgumentException("Unknown crypto suite code: " + code);
    }
}
