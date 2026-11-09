package id.kelompok2.hho.experiment;

import org.apache.commons.math3.stat.inference.MannWhitneyUTest;
import java.util.Arrays;

/**
 * Ringkasan statistik dan uji signifikansi.
 *
 * Paper HHO melaporkan AVG dan STD atas 30 run independen, lalu memakai uji
 * Wilcoxon rank-sum pada taraf 5% untuk menyatakan apakah selisihnya bermakna.
 * Tanpa uji itu, "HHO lebih kecil" bisa saja hanya kebetulan sampel.
 */
public final class Statistics {

    private Statistics() {}

    public static double mean(double[] a) {
        double s = 0; for (double v : a) s += v; return s / a.length;
    }

    /** Simpangan baku populasi (pembagi N), seperti std(...,1) di MATLAB. */
    public static double std(double[] a) {
        double m = mean(a), s = 0;
        for (double v : a) s += (v - m) * (v - m);
        return Math.sqrt(s / a.length);
    }

    public static double median(double[] a) {
        double[] c = a.clone(); Arrays.sort(c);
        int n = c.length;
        return (n % 2 == 1) ? c[n / 2] : 0.5 * (c[n / 2 - 1] + c[n / 2]);
    }

    public static double min(double[] a) { return Arrays.stream(a).min().orElse(Double.NaN); }
    public static double max(double[] a) { return Arrays.stream(a).max().orElse(Double.NaN); }

    /**
     * Nilai p uji Wilcoxon rank-sum (setara Mann-Whitney U) dua sisi.
     * Dipakai paper HHO untuk membandingkan sebaran hasil dua algoritma.
     */
    public static double wilcoxonRankSumP(double[] a, double[] b) {
        // Bila kedua sampel konstan dan identik, tidak ada selisih untuk diuji.
        if (isConstant(a) && isConstant(b) && a[0] == b[0]) return 1.0;
        try {
            return new MannWhitneyUTest().mannWhitneyUTest(a, b);
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    private static boolean isConstant(double[] a) {
        for (int i = 1; i < a.length; i++) if (a[i] != a[0]) return false;
        return true;
    }

    /** Penanda hasil uji: +/=/- dari sudut pandang algoritma pertama (lebih kecil lebih baik). */
    public static String verdict(double[] ours, double[] theirs, double alpha) {
        double p = wilcoxonRankSumP(ours, theirs);
        if (Double.isNaN(p) || p >= alpha) return "=";        // tidak berbeda bermakna
        return mean(ours) < mean(theirs) ? "+" : "-";          // + berarti kita menang
    }

    /** Format ilmiah ringkas untuk tabel hasil. */
    public static String sci(double v) {
        if (v == 0.0) return "0.00E+00";
        if (Double.isNaN(v)) return "NaN";
        return String.format(java.util.Locale.US, "%.2E", v);
    }
}
