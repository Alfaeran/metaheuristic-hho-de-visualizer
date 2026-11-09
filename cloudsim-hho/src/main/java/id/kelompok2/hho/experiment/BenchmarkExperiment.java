package id.kelompok2.hho.experiment;

import id.kelompok2.hho.benchmark.BenchmarkFunctions;
import id.kelompok2.hho.benchmark.Baselines;
import id.kelompok2.hho.core.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.*;
import java.util.*;

/**
 * BENCHMARKING LAPIS B - 23 fungsi uji klasik.
 *
 * Mereproduksi protokol paper HHO (Heidari dkk. 2019 bagian 4):
 *   - N  = 30 search agent
 *   - T  = 500 iterasi
 *   - 30 run independen per fungsi per algoritma
 *   - dim = 30 untuk F1-F13; F14-F23 berdimensi tetap
 *   - pembanding: PSO, GA, DE dengan anggaran yang sama
 *   - uji Wilcoxon rank-sum, alpha = 0.05
 *
 * Keluaran: results/benchmark_functions.csv dan ringkasan ke stdout.
 */
public final class BenchmarkExperiment {

    static final int N_AGENTS = 30;
    static final int T_ITER   = 500;
    static final int RUNS     = 30;
    static final int DIM      = 30;
    static final double ALPHA = 0.05;

    public static void main(String[] args) throws IOException {
        int runs = args.length > 0 ? Integer.parseInt(args[0]) : RUNS;
        int iter = args.length > 1 ? Integer.parseInt(args[1]) : T_ITER;

        System.out.printf("BENCHMARK FUNGSI F1-F23  |  N=%d  T=%d  run=%d  dim(F1-F13)=%d%n",
                N_AGENTS, iter, runs, DIM);
        System.out.println("Protokol mengikuti Heidari dkk. (2019), FGCS 97:849-872.");
        System.out.println();

        Path out = Paths.get("results");
        Files.createDirectories(out);

        StringBuilder csv = new StringBuilder(
            "function,group,dim,optimum,algorithm,avg,std,median,best,worst,"
          + "mean_ms,wilcoxon_p_vs_hho,verdict\n");

        int hhoWins = 0, hhoTies = 0, hhoLosses = 0;

        System.out.printf("%-22s %-5s %-12s %-12s %-12s %-12s%n",
                "Fungsi", "Alg", "AVG", "STD", "p-value", "vs HHO");
        System.out.println("-".repeat(80));

        for (int id = 1; id <= BenchmarkFunctions.COUNT; id++) {
            ObjectiveFunction proto = BenchmarkFunctions.get(id, DIM);
            int dim = proto.dimension();

            Map<String, double[]> results = new LinkedHashMap<>();
            Map<String, double[]> times   = new LinkedHashMap<>();

            for (String alg : new String[]{"HHO", "PSO", "GA", "DE"}) {
                double[] fits = new double[runs];
                double[] ms   = new double[runs];
                for (int r = 0; r < runs; r++) {
                    // Seed berbeda per run TAPI identik antar-algoritma, sehingga
                    // semua algoritma menghadapi rangkaian acak yang setara.
                    long seed = 1000L * id + r;
                    ObjectiveFunction f = BenchmarkFunctions.get(id, DIM);
                    OptimizationResult res = switch (alg) {
                        case "HHO" -> new HarrisHawksOptimization(
                                new HarrisHawksOptimization.Config(N_AGENTS, iter, seed)).optimize(f);
                        case "PSO" -> new Baselines.Pso(N_AGENTS, iter, seed).optimize(f);
                        case "GA"  -> new Baselines.Ga(N_AGENTS, iter, seed).optimize(f);
                        default    -> new Baselines.De(N_AGENTS, iter, seed).optimize(f);
                    };
                    fits[r] = res.bestFitness;
                    ms[r]   = res.elapsedMillis();
                }
                results.put(alg, fits);
                times.put(alg, ms);
            }

            double[] hho = results.get("HHO");
            for (Map.Entry<String, double[]> e : results.entrySet()) {
                String alg = e.getKey();
                double[] v = e.getValue();
                String p = "-", verdict = "-";
                if (!alg.equals("HHO")) {
                    double pv = Statistics.wilcoxonRankSumP(hho, v);
                    p = Statistics.sci(pv);
                    verdict = Statistics.verdict(hho, v, ALPHA);
                    switch (verdict) {
                        case "+" -> hhoWins++;
                        case "-" -> hhoLosses++;
                        default  -> hhoTies++;
                    }
                }
                System.out.printf("%-22s %-5s %-12s %-12s %-12s %-12s%n",
                        alg.equals("HHO") ? proto.name() : "",
                        alg, Statistics.sci(Statistics.mean(v)),
                        Statistics.sci(Statistics.std(v)), p, verdict);

                csv.append(String.format(Locale.US,
                    "%s,%s,%d,%s,%s,%s,%s,%s,%s,%s,%.3f,%s,%s%n",
                    proto.name().replace(",", " "), BenchmarkFunctions.group(id), dim,
                    Statistics.sci(proto.knownOptimum()), alg,
                    Statistics.sci(Statistics.mean(v)), Statistics.sci(Statistics.std(v)),
                    Statistics.sci(Statistics.median(v)), Statistics.sci(Statistics.min(v)),
                    Statistics.sci(Statistics.max(v)),
                    Statistics.mean(times.get(alg)), p, verdict));
            }
            System.out.println();
        }

        try (PrintWriter w = new PrintWriter(
                Files.newBufferedWriter(out.resolve("benchmark_functions.csv")))) {
            w.print(csv);
        }

        System.out.println("=".repeat(80));
        System.out.printf("Wilcoxon rank-sum alpha=%.2f  |  HHO menang %d, seri %d, kalah %d "
                        + "(dari %d perbandingan)%n",
                ALPHA, hhoWins, hhoTies, hhoLosses, hhoWins + hhoTies + hhoLosses);
        System.out.println("Tersimpan: results/benchmark_functions.csv");
    }
}
