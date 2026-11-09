package id.kelompok2.hho.experiment;

import id.kelompok2.hho.core.*;
import id.kelompok2.hho.scheduling.*;
import id.kelompok2.hho.sim.CloudSimScenario;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.*;
import java.util.*;

/**
 * DIAGNOSTIK LANSKAP - menjelaskan MENGAPA metaheuristik kontinu kesulitan
 * pada penjadwalan dengan encoding random key, alih-alih sekadar melaporkan
 * bahwa ia kalah.
 *
 * Hipotesis: fungsi objektif memakai floor(x_i) untuk memilih VM, sehingga
 * lanskapnya BERUNDAK DATAR. Perubahan kecil pada x tidak mengubah makespan
 * sama sekali, dan operator HHO yang semuanya bersifat kontinu kehilangan
 * arah perbaikan. Tiga pengukuran di bawah menguji hipotesis itu.
 */
public final class LandscapeDiagnostics {

    public static void main(String[] args) throws IOException {
        int nTasks = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        int nVms   = args.length > 1 ? Integer.parseInt(args[1]) : 20;

        SchedulingProblem p = CloudSimScenario.problem(nTasks, nVms, nTasks);
        Rng rng = new Rng(4242L);

        System.out.printf("DIAGNOSTIK LANSKAP PENJADWALAN  |  %d task, %d VM%n%n", nTasks, nVms);

        // --- 1. Seberapa datar? Perturbasi kecil vs perubahan makespan
        System.out.println("1. KEDATARAN LANSKAP");
        System.out.println("   Perturbasi x sebesar eps, lalu lihat berapa persen yang mengubah makespan.");
        double[] base = new double[nTasks];
        for (int i = 0; i < nTasks; i++) base[i] = rng.nextDouble() * nVms;
        double baseFit = p.evaluate(base);

        for (double eps : new double[]{0.001, 0.01, 0.1, 0.25, 0.5}) {
            int changed = 0, trials = 2000;
            for (int k = 0; k < trials; k++) {
                double[] y = base.clone();
                int i = rng.nextInt(nTasks);
                y[i] = Math.max(0, Math.min(nVms - 1e-9,
                        y[i] + (rng.nextDouble() * 2 - 1) * eps));
                if (p.evaluate(y) != baseFit) changed++;
            }
            System.out.printf("   eps=%-6.3f -> %5.1f%% perturbasi mengubah makespan%n",
                    eps, 100.0 * changed / trials);
        }

        // --- 2. Berapa banyak jadwal UNIK yang benar-benar dicicipi HHO?
        System.out.println("\n2. KERAGAMAN PENCARIAN");
        System.out.println("   Berapa jadwal berbeda yang sebenarnya dievaluasi HHO?");
        CountingProblem cp = new CountingProblem(p);
        var cfg = new HarrisHawksOptimization.Config(30, 500, 1L);
        var res = new HarrisHawksOptimization(cfg).optimize(cp);
        System.out.printf("   evaluasi fungsi   : %d%n", res.functionEvaluations);
        System.out.printf("   jadwal unik       : %d (%.1f%% dari evaluasi)%n",
                cp.unique.size(), 100.0 * cp.unique.size() / res.functionEvaluations);
        System.out.printf("   ruang pencarian   : %d^%d%n", nVms, nTasks);

        // --- 3. Seberapa dekat heuristik ke batas bawah teoretis?
        System.out.println("\n3. MUTU HEURISTIK TERHADAP BATAS BAWAH");
        double lb = p.lowerBoundMakespan();
        Map<String, int[]> hs = new LinkedHashMap<>();
        hs.put("FCFS", Heuristics.fcfs(p));
        hs.put("SJF", Heuristics.sjf(p));
        hs.put("RR", Heuristics.roundRobin(p));
        hs.put("MinMin", Heuristics.minMin(p));
        hs.put("MaxMin", Heuristics.maxMin(p));
        System.out.printf("   batas bawah teoretis = %.2f s%n", lb);
        for (var e : hs.entrySet()) {
            double mk = p.makespan(e.getValue());
            System.out.printf("   %-8s %10.2f s  (+%.2f%% di atas batas bawah)%n",
                    e.getKey(), mk, 100.0 * (mk - lb) / lb);
        }

        // --- 4. Pencarian lokal diskret: apakah solusi MaxMin masih bisa diperbaiki?
        System.out.println("\n4. APAKAH SOLUSI TERBAIK HEURISTIK MASIH BISA DIPERBAIKI?");
        System.out.println("   Hill-climbing diskret: pindahkan satu task ke VM lain bila membaik.");
        int[] cur = Heuristics.maxMin(p).clone();
        double curMk = p.makespan(cur);
        double startMk = curMk;
        int improvements = 0;
        for (int sweep = 0; sweep < 50; sweep++) {
            boolean moved = false;
            for (int i = 0; i < nTasks; i++) {
                int orig = cur[i];
                for (int v = 0; v < nVms; v++) {
                    if (v == orig) continue;
                    cur[i] = v;
                    double mk = p.makespan(cur);
                    if (mk < curMk - 1e-9) { curMk = mk; moved = true; improvements++; break; }
                    cur[i] = orig;
                }
            }
            if (!moved) break;
        }
        System.out.printf("   MaxMin         : %.2f s%n", startMk);
        System.out.printf("   setelah %3d langkah lokal diskret: %.2f s (%.2f%% lebih baik)%n",
                improvements, curMk, 100.0 * (startMk - curMk) / startMk);
        System.out.printf("   jarak ke batas bawah: %.2f%%%n", 100.0 * (curMk - lb) / lb);

        Path out = Paths.get("results");
        Files.createDirectories(out);
        try (PrintWriter w = new PrintWriter(
                Files.newBufferedWriter(out.resolve("landscape_diagnostics.txt")))) {
            w.printf("tasks=%d vms=%d%n", nTasks, nVms);
            w.printf("lower_bound=%.4f%n", lb);
            w.printf("maxmin=%.4f%n", startMk);
            w.printf("maxmin_after_local_search=%.4f%n", curMk);
            w.printf("hho_unique_schedules=%d of %d evaluations%n",
                    cp.unique.size(), res.functionEvaluations);
        }
        System.out.println("\nTersimpan: results/landscape_diagnostics.txt");
    }

    /** Pembungkus yang mencatat setiap jadwal diskret berbeda yang dievaluasi. */
    private static final class CountingProblem implements ObjectiveFunction {
        final SchedulingProblem base;
        final Set<String> unique = new HashSet<>();
        CountingProblem(SchedulingProblem b) { this.base = b; }
        @Override public double evaluate(double[] x) {
            unique.add(Arrays.toString(base.decode(x)));
            return base.evaluate(x);
        }
        @Override public int dimension() { return base.dimension(); }
        @Override public double[] lowerBound() { return base.lowerBound(); }
        @Override public double[] upperBound() { return base.upperBound(); }
        @Override public String name() { return base.name(); }
    }

    private LandscapeDiagnostics() {}
}
