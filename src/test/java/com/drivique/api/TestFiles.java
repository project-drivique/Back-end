package com.drivique.api;

/** Archivos mínimos con la firma real de cada formato (las subidas validan el contenido, no solo la extensión). */
public final class TestFiles {
    public static final byte[] JPEG = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};
    public static final byte[] PNG = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};

    private TestFiles() {
    }
}
