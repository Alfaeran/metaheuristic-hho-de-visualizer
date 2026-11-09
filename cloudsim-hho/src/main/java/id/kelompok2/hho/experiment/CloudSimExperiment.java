package id.kelompok2.hho.experiment;

import id.kelompok2.hho.benchmark.Baselines;
import id.kelompok2.hho.core.*;
import id.kelompok2.hho.scheduling.*;
import id.kelompok2.hho.sim.CloudSimScenario;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.*;
import java.util.*;

/**
 * BENCHMARKING LAPIS C - penjadwalan task di CloudSim.
 *
 * Konfigurasi mengikuti EHHO (IJACSA 15(7) 2024, Tabel I): 5 datacenter,
 * 20 VM, jumlah task 100-1000, panjang dasar 780.000 MI.
 *
 * Pembanding:
 *   heuristik sekali jalan  : FCFS, SJF, Round-Robin, Min-Min, Max-Min
 *   metaheuristik berpopulasi: HHO, PSO, GA, DE (anggaran identik)
 *
 * Setiap jadwal yang dihasilkan DIJALANKAN di CloudSim, dan metrik diambil
 * dari hasil simulasi, bukan dari rumus. Metrik: makespan, waktu respons
 * rata-rata, utilisasi VM, degree of imbalance, dan throughput.
 */
public final class CloudSimExperiment {

    static final int[] TASK_SIZES = {100, 200, 500, 1000};
    static final int   VM_COUNT   = CloudSimScenario.VM_COUNT;   // 20
    static final int   N_AGENTS   = 30;

    public static void main(String[] args) throws IOException {
        int runs = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        int iter = args.length > 1 ? Integer.parseInt(args[1]) : 200;

        System.out.printf("BENCHMARK PENJADWALAN CLOUDSIM  |  %d datacenter, %d VM, "
                        + "N=%d, T=%d, run=%d%n",
                CloudSimScenario.DATACENTERS, VM_COUNT, N_AGENTS, iter, runs);
        System.out.println("Konfigurasi mengikuti EHHO (IJACSA 15(7) 2024, Tabel I).");
        System.out.println();

        Path out = Paths.get("results");
        Files.createDirectories(out);
        StringBuilder csv = new StringBuilder(
            "tasks,vms,algorithm,type,makespan_avg,makespan_std,makespan_best,"
          + "response_avg,utilization_avg,imbalance_avg,throughput_avg,"
          + "sched_time_ms_avg,improvement_vs_fcfs_pct,wilcoxon_p_vs_hho,verdict\n");

        for (int nTasks : TASK_SIZES) {
            System.out.println("=".repeat(104));
            System.out.printf("TASK = %d   VM = %d%n", nTasks, VM_COUNT);
            System.out.println("-".repeat(104));
            System.out.printf("%-10s %-10s %-12s %-12s %-10s %-10s %-10s %-10s %-8s%n",
                "Algoritma", "Jenis", "Makespan", "STD", "Respons", "Utilisasi",
                "Imbalance", "Waktu(ms)", "vs HHO");

            SchedulingProblem p = CloudSimScenario.problem(nTasks, VM_COUNT, nTasks);
            double lbTheory = p.lowerBoundMakespan();

            Map<String, double[]> makespans = new LinkedHashMap<>();
            Map<String, CloudSimScenario.SimOutcome> sample = new LinkedHashMap<>();
            Map<String, Double> schedMs = new LinkedHashMap<>();
            Map<String, String> kind = new LinkedHashMap<>();
            Map<String, double[]> respAll = new LinkedHashMap<>();
            Map<String, double[]> utilAll = new LinkedHashMap<>();
            Map<String, double[]> imbAll  = new LinkedHashMap<>();
            Map<String, double[]> thrAll  = new LinkedHashMap<>();

            // ---------- heuristik deterministik: satu kali jalan sudah cukup
            for (var e : new LinkedHashMap<String, int[]>() {{
                    put("FCFS",   Heuristics.fcfs(p));
                    put("SJF",    Heuristics.sjf(p));
                    put("RR",     Heuristics.roundRobin(p));
                    put("MinMin", Heuristics.minMin(p));
                    put("MaxMin", Heuristics.maxMin(p));
                }}.entrySet()) {
                long t0 = System.nanoTime();
                int[] a = e.getValue();
                double ms = (System.nanoTime() - t0) / 1e6;
                var o = CloudSimScenario.run(p.taskLengths(), p.vmMipsArray(), a);
                makespans.put(e.getKey(), new double[]{o.makespan});
                respAll.put(e.getKey(), new double[]{o.avgResponseTime});
                utilAll.put(e.getKey(), new double[]{o.avgVmUtilization});
                imbAll.put(e.getKey(),  new double[]{o.degreeOfImbalance});
                thrAll.put(e.getKey(),  new double[]{o.throughput});
                sample.put(e.getKey(), o);
                schedMs.put(e.getKey(), ms);
                kind.put(e.getKey(), "heuristik");
            }

            // ---------- metaheuristik: stokastik, perlu banyak run
            for (String alg : new String[]{"HHO", "PSO", "GA", "DE"}) {
                double[] mk = new double[runs], rp = new double[runs],
                         ut = new double[runs], im = new double[runs],
                         th = new double[runs], sm = new double[runs];
                CloudSimScenario.SimOutcome bestOutcome = null;
                double bestMk = Double.MAX_VALUE;

                for (int r = 0; r < runs; r++) {
                    long seed = 7_000L * nTasks + r;    // seed sama untuk semua algoritma
                    Optimizer opt = switch (alg) {
                        case "HHO" -> new HarrisHawksOptimization(
                                new HarrisHawksOptimization.Config(N_AGENTS, iter, seed));
                        case "PSO" -> new Baselines.Pso(N_AGENTS, iter, seed);
                        case "GA"  -> new Baselines.Ga(N_AGENTS, iter, seed);
                        default    -> new Baselines.De(N_AGENTS, iter, seed);
                    };
                    OptimizationResult res = opt.optimize(p);
                    sm[r] = res.elapsedMillis();

                    int[] a = p.decode(res.bestPosition);
                    var o = CloudSimScenario.run(p.taskLengths(), p.vmMipsArray(), a);
                    mk[r] = o.makespan; rp[r] = o.avgResponseTime;
                    ut[r] = o.avgVmUtilization; im[r] = o.degreeOfImbalance;
                    th[r] = o.throughput;
                    if (o.makespan < bestMk) { bestMk = o.makespan; bestOutcome = o; }
                }
                makespans.put(alg, mk); respAll.put(alg, rp); utilAll.put(alg, ut);
                imbAll.put(alg, im);    thrAll.put(alg, th);
                sample.put(alg, bestOutcome);
                schedMs.put(alg, Statistics.mean(sm));
                kind.put(alg, "metaheuristik");
            }

            // ---------- laporan
            double fcfsMk = makespans.get("FCFS")[0];
            double[] hhoMk = makespans.get("HHO");

            for (String alg : makespans.keySet()) {
                double[] mk = makespans.get(alg);
                double avg = Statistics.mean(mk);
                String verdict = alg.equals("HHO") ? "-" :
                        (mk.length > 1 ? Statistics.verdict(hhoMk, mk, 0.05)
                                       : (avg > Statistics.mean(hhoMk) ? "+" : "-"));
                String pval = (alg.equals("HHO") || mk.length == 1) ? "-"
                        : Statistics.sci(Statistics.wilcoxonRankSumP(hhoMk, mk));

                System.out.printf("%-10s %-10s %-12.2f %-12.2f %-10.2f %-10.4f %-10.4f %-10.1f %-8s%n",
                        alg, kind.get(alg), avg, Statistics.std(mk),
                        Statistics.mean(respAll.get(alg)),
                        Statistics.mean(utilAll.get(alg)),
                        Statistics.mean(imbAll.get(alg)),
                        schedMs.get(alg), verdict);

                csv.append(String.format(Locale.US,
                    "%d,%d,%s,%s,%.4f,%.4f,%.4f,%.4f,%.6f,%.6f,%.6f,%.3f,%.2f,%s,%s%n",
                    nTasks, VM_COUNT, alg, kind.get(alg),
                    avg, Statistics.std(mk), Statistics.min(mk),
                    Statistics.mean(respAll.get(alg)), Statistics.mean(utilAll.get(alg)),
                    Statistics.mean(imbAll.get(alg)), Statistics.mean(thrAll.get(alg)),
                    schedMs.get(alg), 100.0 * (fcfsMk - avg) / fcfsMk, pval, verdict));
            }
            System.out.printf("%nBatas bawah teoretis makespan: %.2f s "
                            + "(total beban / total kapasitas)%n", lbTheory);
            double hhoAvg = Statistics.mean(hhoMk);
            System.out.printf("HHO terhadap batas bawah: %.1f%% di atasnya | "
                            + "perbaikan atas FCFS: %.2f%%%n%n",
                    100.0 * (hhoAvg - lbTheory) / lbTheory,
                    100.0 * (fcfsMk - hhoAvg) / fcfsMk);
        }

        try (PrintWriter w = new PrintWriter(
                Files.newBufferedWriter(out.resolve("cloudsim_scheduling.csv")))) {
            w.print(csv);
        }
        System.out.println("Tersimpan: results/cloudsim_scheduling.csv");
    }

    private CloudSimExperiment() {}
}
