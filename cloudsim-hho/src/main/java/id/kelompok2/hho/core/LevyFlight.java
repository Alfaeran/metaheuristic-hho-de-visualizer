package id.kelompok2.hho.core;

/**
 * Lévy flight (Mantegna) untuk tahap rapid dive HHO.
 *
 * Paper Heidari dkk. 2019, persamaan (9):
 *     LF(x) = 0.01 * (u * sigma) / |v|^(1/beta)
 *     sigma = ( G(1+beta) * sin(pi*beta/2) / ( G((1+beta)/2) * beta * 2^((beta-1)/2) ) )^(1/beta)
 * dengan beta = 1.5, u dan v ~ N(0,1).
 *
 * CATATAN PENTING - selisih antara teks paper dan kode resmi penulis:
 * fungsi Levy() pada HHO.m resmi (github.com/aliasgharheidaricom) TIDAK memuat
 * faktor 0.01; ia mengembalikan u*sigma/|v|^(1/beta) saja. Perbedaannya 100x
 * pada ukuran langkah, jadi keduanya TIDAK ekuivalen. Karena angka pada paper
 * dihasilkan oleh kode itu, mode MATLAB_CODE dipakai sebagai bawaan, sedangkan
 * mode PAPER_EQ9 disediakan agar selisihnya bisa diuji, bukan diasumsikan.
 */
public final class LevyFlight {

    public enum Variant {
        /** Sesuai HHO.m resmi: tanpa faktor 0.01. Bawaan. */
        MATLAB_CODE,
        /** Sesuai persamaan (9) pada teks paper: dengan faktor 0.01. */
        PAPER_EQ9
    }

    public static final double BETA = 1.5;

    /** sigma Mantegna untuk beta = 1.5. Dihitung sekali, bukan di-hardcode. */
    public static final double SIGMA = computeSigma(BETA);

    private LevyFlight() {}

    static double computeSigma(double beta) {
        double num = gamma(1.0 + beta) * Math.sin(Math.PI * beta / 2.0);
        double den = gamma((1.0 + beta) / 2.0) * beta * Math.pow(2.0, (beta - 1.0) / 2.0);
        return Math.pow(num / den, 1.0 / beta);
    }

    /** Satu vektor langkah Lévy berdimensi d. */
    public static double[] step(int d, Rng rng, Variant variant) {
        double[] out = new double[d];
        double scale = (variant == Variant.PAPER_EQ9) ? 0.01 : 1.0;
        for (int i = 0; i < d; i++) {
            double u = rng.nextGaussian() * SIGMA;
            double v = rng.nextGaussian();
            out[i] = scale * u / Math.pow(Math.abs(v), 1.0 / BETA);
        }
        return out;
    }

    /** Fungsi Gamma via aproksimasi Lanczos (g=7, n=9). */
    public static double gamma(double z) {
        final double[] g = {
            0.99999999999980993, 676.5203681218851, -1259.1392167224028,
            771.32342877765313, -176.61502916214059, 12.507343278686905,
            -0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7
        };
        if (z < 0.5) {
            return Math.PI / (Math.sin(Math.PI * z) * gamma(1.0 - z));
        }
        z -= 1.0;
        double x = g[0];
        for (int i = 1; i < 9; i++) x += g[i] / (z + i);
        double t = z + 7.5;
        return Math.sqrt(2.0 * Math.PI) * Math.pow(t, z + 0.5) * Math.exp(-t) * x;
    }
}
