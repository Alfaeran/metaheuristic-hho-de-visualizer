package id.kelompok2.hho.experiment;

import id.kelompok2.hho.benchmark.BenchmarkFunctions;
import id.kelompok2.hho.benchmark.Baselines;
import id.kelompok2.hho.core.*;
import id.kelompok2.hho.scheduling.SchedulingProblem;
import id.kelompok2.hho.sim.CloudSimScenario;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.*;
import java.util.Locale;

/**
 * PERFORMANCE TEST LAPIS D-2 - biaya komputasi dan skalabilitas.
 *
 * Mengukur tiga hal yang menentukan apakah algoritma layak dipakai, bukan
 * hanya apakah hasilnya bagus:
 *   1. skalabilitas waktu terhadap DIMENSI masalah
 *   2. skalabilitas waktu terhadap UKURAN POPULASI dan ITERASI
 *   3. biaya simulasi CloudSim terhadap jumlah task
 *
 * Setiap pengukuran didahului pemanasan JIT agar angkanya bukan artefak
 * kompilasi JVM.
 */
public final class PerformanceTest {

    static final int WARMUP = 3;
    static final int REPEAT = 5;

    public static void main(String[] args) throws IOException {
        StringBuilder csv = new StringBuilder("scenario,parameter,value,metric,result\n");

        System.out.println("PERFORMANCE TEST");
        System.out.println("Setiap angka adalah rata-rata " + REPEAT
                + " pengulangan setelah " + WARMUP + " kali pemanasan JIT.\n");

        // ---------- 1. skalabilitas terhadap dimensi
        System.out.println("1. SKALABILITAS TERHADAP DIMENSI (Sphere, N=30, T=500)");
        System.out.printf("   %-10s %-14s %-14s %-12s%n", "dim", "waktu(ms)", "evaluasi", "ms/1k eval");
        for (int dim : new int[]{10, 30, 50, 100, 200, 500}) {
            var f = BenchmarkFunctions.get(1, dim);
            for (int w = 0; w < WARMUP; w++)
                new HarrisHawksOptimization(new HarrisHawksOptimization.Config(30, 100, w)).optimize(f);
            double ms = 0; long ev = 0;
            for (int r = 0; r < REPEAT; r++) {
                var res = new HarrisHawksOptimization(
                        new HarrisHawksOptimization.Config(30, 500, r)).optimize(f);
                ms += res.elapsedMillis(); ev = res.functionEvaluations;
            }
            ms /= REPEAT;
            System.out.printf("   %-10d %-14.1f %-14d %-12.3f%n", dim, ms, ev, ms / (ev / 1000.0));
            csv.append(String.format(Locale.US, "dimension_scaling,dim,%d,time_ms,%.3f%n", dim, ms));
        }

        // ---------- 2. skalabilitas terhadap populasi dan iterasi
        System.out.println("\n2. SKALABILITAS TERHADAP POPULASI DAN ITERASI (Sphere dim=30)");
        System.out.printf("   %-10s %-10s %-14s%n", "N", "T", "waktu(ms)");
        var f30 = BenchmarkFunctions.get(1, 30);
        for (int[] nt : new int[][]{{10,500},{30,500},{50,500},{100,500},{30,1000},{30,2000}}) {
            for (int w = 0; w < WARMUP; w++)
                new HarrisHawksOptimization(new HarrisHawksOptimization.Config(nt[0], 50, w)).optimize(f30);
            double ms = 0;
            for (int r = 0; r < REPEAT; r++)
                ms += new HarrisHawksOptimization(
                        new HarrisHawksOptimization.Config(nt[0], nt[1], r)).optimize(f30).elapsedMillis();
            ms /= REPEAT;
            System.out.printf("   %-10d %-10d %-14.1f%n", nt[0], nt[1], ms);
            csv.append(String.format(Locale.US,
                "population_scaling,N_T,%dx%d,time_ms,%.3f%n", nt[0], nt[1], ms));
        }

        // ---------- 3. perbandingan biaya antar-algoritma
        System.out.println("\n3. BIAYA PER ALGORITMA (Sphere dim=30, N=30, T=500)");
        System.out.printf("   %-8s %-14s %-14s%n", "Alg", "waktu(ms)", "evaluasi");
        for (String alg : new String[]{"HHO", "PSO", "GA", "DE"}) {
            double ms = 0; long ev = 0;
            for (int r = 0; r < REPEAT; r++) {
                Optimizer o = switch (alg) {
                    case "HHO" -> new HarrisHawksOptimization(
                            new HarrisHawksOptimization.Config(30, 500, r));
                    case "PSO" -> new Baselines.Pso(30, 500, r);
                    case "GA"  -> new Baselines.Ga(30, 500, r);
                    default    -> new Baselines.De(30, 500, r);
                };
                var res = o.optimize(f30);
                ms += res.elapsedMillis(); ev = res.functionEvaluations;
            }
            ms /= REPEAT;
            System.out.printf("   %-8s %-14.1f %-14d%n", alg, ms, ev);
            csv.append(String.format(Locale.US,
                "algorithm_cost,algorithm,%s,time_ms,%.3f%n", alg, ms));
        }

        // ---------- 4. biaya simulasi CloudSim
        System.out.println("\n4. BIAYA SIMULASI CLOUDSIM (20 VM, 5 datacenter)");
        System.out.printf("   %-10s %-16s %-16s%n", "task", "waktu sim(ms)", "ms per task");
        for (int n : new int[]{100, 200, 500, 1000, 2000}) {
            SchedulingProblem p = CloudSimScenario.problem(n, 20, n);
            int[] a = id.kelompok2.hho.scheduling.Heuristics.minMin(p);
            for (int w = 0; w < 2; w++) CloudSimScenario.run(p.taskLengths(), p.vmMipsArray(), a);
            long t0 = System.nanoTime();
            for (int r = 0; r < 3; r++) CloudSimScenario.run(p.taskLengths(), p.vmMipsArray(), a);
            double ms = (System.nanoTime() - t0) / 1e6 / 3;
            System.out.printf("   %-10d %-16.1f %-16.4f%n", n, ms, ms / n);
            csv.append(String.format(Locale.US,
                "cloudsim_cost,tasks,%d,time_ms,%.3f%n", n, ms));
        }

        // ---------- 5. jejak memori
        System.out.println("\n5. JEJAK MEMORI PUNCAK (Sphere, N=30, T=500)");
        for (int dim : new int[]{30, 200, 1000}) {
            System.gc();
            Runtime rt = Runtime.getRuntime();
            long before = rt.totalMemory() - rt.freeMemory();
            new HarrisHawksOptimization(new HarrisHawksOptimization.Config(30, 500, 1))
                    .optimize(BenchmarkFunctions.get(1, dim));
            long after = rt.totalMemory() - rt.freeMemory();
            double mb = Math.max(0, after - before) / (1024.0 * 1024.0);
            System.out.printf("   dim=%-6d  delta heap = %.2f MB%n", dim, mb);
            csv.append(String.format(Locale.US, "memory,dim,%d,heap_mb,%.3f%n", dim, mb));
        }

        Path out = Paths.get("results");
        Files.createDirectories(out);
        try (PrintWriter w = new PrintWriter(
                Files.newBufferedWriter(out.resolve("performance.csv")))) { w.print(csv); }
        System.out.println("\nTersimpan: results/performance.csv");
    }

    private PerformanceTest() {}
}
