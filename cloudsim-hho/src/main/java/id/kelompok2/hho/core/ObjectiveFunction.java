package id.kelompok2.hho.core;

/** Fungsi objektif yang diminimalkan, beserta domain pencariannya. */
public interface ObjectiveFunction {
    /** Nilai fungsi pada titik x (semakin kecil semakin baik). */
    double evaluate(double[] x);

    /** Dimensi masalah. */
    int dimension();

    /** Batas bawah per dimensi. */
    double[] lowerBound();

    /** Batas atas per dimensi. */
    double[] upperBound();

    /** Nama untuk pelaporan. */
    String name();

    /**
     * Optimum global yang diketahui secara analitik, dipakai sebagai acuan
     * kebenaran pada benchmark. NaN bila tidak diketahui.
     */
    default double knownOptimum() { return Double.NaN; }
}
