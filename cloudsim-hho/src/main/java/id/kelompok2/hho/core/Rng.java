package id.kelompok2.hho.core;

/**
 * PRNG ber-seed (xoshiro256++) agar setiap eksperimen dapat diulang persis.
 * Reprodusibilitas adalah syarat pengujian ilmiah: tanpa seed tetap, angka
 * benchmark tidak bisa diverifikasi ulang oleh penguji.
 */
public final class Rng {
    private long s0, s1, s2, s3;
    private double cachedGaussian = Double.NaN;

    public Rng(long seed) {
        // SplitMix64 untuk menyebar seed awal ke empat state
        long z = seed;
        for (int i = 0; i < 4; i++) {
            z += 0x9E3779B97F4A7C15L;
            long x = z;
            x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
            x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
            x = x ^ (x >>> 31);
            switch (i) {
                case 0 -> s0 = x;
                case 1 -> s1 = x;
                case 2 -> s2 = x;
                default -> s3 = x;
            }
        }
    }

    private long nextLong() {
        final long result = Long.rotateLeft(s0 + s3, 23) + s0;
        final long t = s1 << 17;
        s2 ^= s0; s3 ^= s1; s1 ^= s2; s0 ^= s3; s2 ^= t;
        s3 = Long.rotateLeft(s3, 45);
        return result;
    }

    /** Seragam pada [0,1). Setara rand() di MATLAB. */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    /** Bilangan bulat seragam pada [0,bound). */
    public int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("bound harus > 0");
        return (int) Math.floorMod(nextLong(), (long) bound);
    }

    /** Normal baku N(0,1) via Box-Muller. Setara randn() di MATLAB. */
    public double nextGaussian() {
        if (!Double.isNaN(cachedGaussian)) {
            double v = cachedGaussian;
            cachedGaussian = Double.NaN;
            return v;
        }
        double u1, u2;
        do { u1 = nextDouble(); } while (u1 <= 1e-300);
        u2 = nextDouble();
        double r = Math.sqrt(-2.0 * Math.log(u1));
        double theta = 2.0 * Math.PI * u2;
        cachedGaussian = r * Math.sin(theta);
        return r * Math.cos(theta);
    }
}
