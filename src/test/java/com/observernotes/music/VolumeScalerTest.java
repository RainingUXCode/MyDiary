package com.observernotes.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VolumeScalerTest {

    private static byte[] sample(int value) {
        return new byte[] {(byte) value, (byte) (value >> 8)};
    }

    private static int read(byte[] b) {
        return (short) ((b[0] & 0xFF) | (b[1] << 8));
    }

    @Test
    void fullVolumeLeavesAudioUntouched() {
        byte[] b = sample(12345);
        VolumeScaler.apply(b, 2, 1.0);
        assertEquals(12345, read(b));
    }

    @Test
    void zeroVolumeSilencesPositiveAndNegativeSamples() {
        byte[] p = sample(20000);
        byte[] n = sample(-20000);
        VolumeScaler.apply(p, 2, 0.0);
        VolumeScaler.apply(n, 2, 0.0);
        assertEquals(0, read(p));
        assertEquals(0, read(n));
    }

    @Test
    void halfVolumeIsAQuarterOfTheAmplitude() {
        byte[] b = sample(20000);
        VolumeScaler.apply(b, 2, 0.5);
        assertEquals(5000, read(b));
        byte[] n = sample(-20000);
        VolumeScaler.apply(n, 2, 0.5);
        assertEquals(-5000, read(n));
    }

    @Test
    void outOfRangeVolumeIsClampedAndOddTrailingByteIsIgnored() {
        assertEquals(0.0, VolumeScaler.gain(-3), 1e-9);
        assertEquals(1.0, VolumeScaler.gain(7), 1e-9);
        byte[] b = {10, 0, 99};
        VolumeScaler.apply(b, 3, 0.0);
        assertEquals(0, read(b));
        assertEquals(99, b[2]);
        assertTrue(VolumeScaler.gain(0.3) < VolumeScaler.gain(0.6));
    }
}
