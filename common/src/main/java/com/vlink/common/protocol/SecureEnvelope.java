package com.vlink.common.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Arrays;

public final class SecureEnvelope {
    private final byte version;
    private final MessageType messageType;
    private final CryptoSuite cryptoSuite;
    private final NodeId senderId;
    private final long timestampSec;
    private final byte[] nonce;
    private final byte[] ciphertext;

    public SecureEnvelope(
        byte version,
        MessageType messageType,
        CryptoSuite cryptoSuite,
        NodeId senderId,
        long timestampSec,
        byte[] nonce,
        byte[] ciphertext
    ) {
        this.version = version;
        this.messageType = messageType;
        this.cryptoSuite = cryptoSuite;
        this.senderId = senderId;
        this.timestampSec = timestampSec;
        this.nonce = Arrays.copyOf(nonce, nonce.length);
        this.ciphertext = Arrays.copyOf(ciphertext, ciphertext.length);
    }

    public MessageType messageType() {
        return messageType;
    }

    public CryptoSuite cryptoSuite() {
        return cryptoSuite;
    }

    public NodeId senderId() {
        return senderId;
    }

    public long timestampSec() {
        return timestampSec;
    }

    public byte[] nonce() {
        return Arrays.copyOf(nonce, nonce.length);
    }

    public byte[] ciphertext() {
        return Arrays.copyOf(ciphertext, ciphertext.length);
    }

    public byte[] serialize() {
        ByteBuf out = Unpooled.buffer();
        try {
            out.writeShort(ProtocolConstants.MAGIC);
            out.writeByte(version);
            out.writeByte(0x01); // encrypted flag
            out.writeByte(messageType.code());
            out.writeByte(cryptoSuite.code());
            out.writeBytes(senderId.toBytes());
            out.writeInt((int) timestampSec);
            out.writeByte(nonce.length);
            out.writeBytes(nonce);
            out.writeShort(ciphertext.length);
            out.writeBytes(ciphertext);
            byte[] result = new byte[out.readableBytes()];
            out.readBytes(result);
            return result;
        } finally {
            out.release();
        }
    }

    public static SecureEnvelope deserialize(byte[] packet) {
        ByteBuf in = Unpooled.wrappedBuffer(packet);
        try {
            short magic = in.readShort();
            if (magic != ProtocolConstants.MAGIC) {
                throw new IllegalArgumentException("Invalid magic");
            }
            byte version = in.readByte();
            byte flags = in.readByte();
            if ((flags & 0x01) == 0) {
                throw new IllegalArgumentException("Packet is not encrypted");
            }
            MessageType messageType = MessageType.fromCode(in.readByte());
            CryptoSuite cryptoSuite = CryptoSuite.fromCode(in.readByte());
            byte[] sender = new byte[ProtocolConstants.NODE_ID_LEN];
            in.readBytes(sender);
            long timestampSec = in.readUnsignedInt();
            int nonceLen = in.readUnsignedByte();
            byte[] nonce = new byte[nonceLen];
            in.readBytes(nonce);
            int cipherLen = in.readUnsignedShort();
            byte[] ciphertext = new byte[cipherLen];
            in.readBytes(ciphertext);
            return new SecureEnvelope(version, messageType, cryptoSuite, NodeId.fromBytes(sender), timestampSec, nonce, ciphertext);
        } finally {
            in.release();
        }
    }
}

