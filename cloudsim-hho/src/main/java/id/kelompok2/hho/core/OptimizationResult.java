package id.kelompok2.hho.core;

/** Hasil satu kali menjalankan sebuah optimizer. */
public class OptimizationResult {
    public double[] bestPosition;
    public double   bestFitness;
    public double[] convergenceCurve;
    public long     functionEvaluations;
    public long     elapsedNanos;

    public double elapsedMillis() { return elapsedNanos / 1e6; }
}
