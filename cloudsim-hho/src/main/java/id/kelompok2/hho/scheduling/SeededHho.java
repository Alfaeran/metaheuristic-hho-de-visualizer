package id.kelompok2.hho.scheduling;

import id.kelompok2.hho.core.*;

/**
 * HHO dengan INISIALISASI TERINFORMASI.
 *
 * Latar belakang: pada penjadwalan, dimensi masalah sama dengan jumlah task,
 * sehingga ruang pencarian M^N sangat besar (20^100 kira-kira 1e130). Populasi
 * awal yang sepenuhnya acak hampir pasti jauh lebih buruk daripada heuristik
 * greedy, dan anggaran iterasi yang wajar tidak cukup untuk mengejarnya.
 *
 * Praktik lazim di literatur penjadwalan metaheuristik adalah menyisipkan
 * solusi heuristik ke dalam populasi awal. Yang diubah HANYA inisialisasi:
 * empat individu pertama berasal dari Min-Min, Max-Min, FCFS, dan SJF,
 * sisanya tetap acak agar keragaman terjaga. Seluruh persamaan (1)-(11) HHO
 * berjalan apa adanya, sehingga perbandingan terhadap HHO murni mengisolasi
 * pengaruh inisialisasi.
 *
 * Dilaporkan TERPISAH dari HHO murni agar tidak mengaburkan pertanyaan
 * "apakah HHO baku bekerja sesuai teorinya".
 */
public final class SeededHho {

    private SeededHho() {}

    /** Ubah penugasan diskret menjadi vektor kontinu yang decode()-nya persis sama. */
    public static double[] toVector(int[] assignment) {
        double[] v = new double[assignment.length];
        for (int i = 0; i < v.length; i++) v[i] = assignment[i] + 0.5;  // tengah sel
        return v;
    }

    public static OptimizationResult optimize(SchedulingProblem p, int n, int t, long seed) {
        // Empat heuristik sebagai benih populasi awal.
        double[][] seeds = {
            toVector(Heuristics.minMin(p)),
            toVector(Heuristics.maxMin(p)),
            toVector(Heuristics.fcfs(p)),
            toVector(Heuristics.sjf(p))
        };

        var cfg = new HarrisHawksOptimization.Config(n, t, seed);
        cfg.initialPopulation = seeds;          // HHO baku, hanya awalnya diubah
        return new HarrisHawksOptimization(cfg).optimize(p);
    }
}
