package com.vlink.edge.tun;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class MockTunDevice implements TunDevice {
    private final String name;
    private final InputStream input;
    private final OutputStream output;

    public MockTunDevice(String name, InputStream input, OutputStream output) {
        this.name = name;
        this.input = new BufferedInputStream(input);
        this.output = new BufferedOutputStream(output);
    }

    public static MockTunDevice fromFiles(String name, Path inputFile, Path outputFile) throws IOException {
        InputStream in = Files.newInputStream(inputFile, StandardOpenOption.READ);
        OutputStream out = Files.newOutputStream(
            outputFile,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        );
        return new MockTunDevice(name, in, out);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        return input.read(buffer);
    }

    @Override
    public void write(byte[] packet, int offset, int length) throws IOException {
        output.write(packet, offset, length);
        output.flush();
    }

    @Override
    public void close() throws IOException {
        IOException first = null;
        try {
            input.close();
        } catch (IOException ex) {
            first = ex;
        }
        try {
            output.close();
        } catch (IOException ex) {
            if (first == null) {
                first = ex;
            }
        }
        if (first != null) {
            throw first;
        }
    }
}
