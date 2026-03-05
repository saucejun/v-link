package com.vlink.common.protocol;

import com.vlink.common.crypto.AesGcmCodec;
import com.vlink.common.crypto.PskKey;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.security.SecureRandom;

public final class EnvelopeCodec {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private EnvelopeCodec() {
    }

    public static byte[] encode(String psk, ControlMessage message, NodeId senderId, long timestampSec) {
        byte[] nonce = new byte[ProtocolConstants.NONCE_LEN];
        SECURE_RANDOM.nextBytes(nonce);

        byte[] key = PskKey.deriveAes128Key(psk);
        byte[] plaintext = PacketCodec.encode(message);
        byte[] aad = buildAad(message.type(), senderId, timestampSec, nonce);
        byte[] ciphertext = AesGcmCodec.encrypt(key, nonce, aad, plaintext);

        SecureEnvelope envelope = new SecureEnvelope(
            ProtocolConstants.VERSION,
            message.type(),
            CryptoSuite.AES_128_GCM,
            senderId,
            timestampSec,
            nonce,
            ciphertext
        );
        return envelope.serialize();
    }

    public static DecodedEnvelope decode(String psk, byte[] packet) {
        SecureEnvelope envelope = SecureEnvelope.deserialize(packet);
        if (envelope.cryptoSuite() != CryptoSuite.AES_128_GCM) {
            throw new IllegalArgumentException("Unsupported crypto suite: " + envelope.cryptoSuite());
        }

        byte[] key = PskKey.deriveAes128Key(psk);
        byte[] aad = buildAad(envelope.messageType(), envelope.senderId(), envelope.timestampSec(), envelope.nonce());
        byte[] plaintext = AesGcmCodec.decrypt(key, envelope.nonce(), aad, envelope.ciphertext());
        ControlMessage message = PacketCodec.decode(plaintext);
        if (message.type() != envelope.messageType()) {
            throw new IllegalArgumentException("Message type mismatch in envelope");
        }
        return new DecodedEnvelope(envelope.senderId(), envelope.timestampSec(), message);
    }

    private static byte[] buildAad(MessageType type, NodeId senderId, long timestampSec, byte[] nonce) {
        ByteBuf out = Unpooled.buffer();
        try {
            out.writeShort(ProtocolConstants.MAGIC);
            out.writeByte(ProtocolConstants.VERSION);
            out.writeByte(type.code());
            out.writeBytes(senderId.toBytes());
            out.writeInt((int) timestampSec);
            out.writeByte(nonce.length);
            out.writeBytes(nonce);
            byte[] aad = new byte[out.readableBytes()];
            out.readBytes(aad);
            return aad;
        } finally {
            out.release();
        }
    }

    public static final class DecodedEnvelope {
        private final NodeId senderId;
        private final long timestampSec;
        private final ControlMessage message;

        public DecodedEnvelope(NodeId senderId, long timestampSec, ControlMessage message) {
            this.senderId = senderId;
            this.timestampSec = timestampSec;
            this.message = message;
        }

        public NodeId senderId() {
            return senderId;
        }

        public long timestampSec() {
            return timestampSec;
        }

        public ControlMessage message() {
            return message;
        }
    }
}

