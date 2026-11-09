package id.kelompok2.hho.experiment;

import id.kelompok2.hho.benchmark.Baselines;
import id.kelompok2.hho.core.*;
import id.kelompok2.hho.scheduling.*;
import id.kelompok2.hho.sim.CloudSimScenario;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.*;
import java.util.Locale;

/**
 * PERFORMANCE TEST LAPIS D-1 - anggaran iterasi versus mutu solusi.
 *
 * Menjawab pertanyaan yang muncul dari benchmark penjadwalan: pada anggaran
 * iterasi kecil, metaheuristik kalah dari heuristik greedy. Apakah itu karena
 * iterasinya kurang, atau karena batasan mendasar encoding random key?
 *
 * Ruang pencariannya M^N: untuk 100 task dan 20 VM, 20^100 kira-kira 1e130
 * kemungkinan. Dengan N=30 dan T iterasi, HHO hanya mencicipi sekitar 30*T
 * titik. Eksperimen ini mengukur berapa anggaran yang dibutuhkan agar HHO
 * menyamai heuristik terbaik, dan apakah itu tercapai dalam batas wajar.
 */
public final class ConvergenceExperiment {

    static final int[] BUDGETS = {50, 100, 200, 500, 1000, 2000, 5000};
    static final int   N_AGENTS = 30;
    static final int   RUNS     = 5;

    public static void main(String[] args) throws IOException {
        int nTasks = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        int nVms   = args.length > 1 ? Integer.parseInt(args[1]) : 20;

        SchedulingProblem p = CloudSimScenario.problem(nTasks, nVms, nTasks);
        double lb = p.lowerBoundMakespan();

        // Acuan heuristik terbaik
        double mkFcfs   = p.makespan(Heuristics.fcfs(p));
        double mkMinMin = p.makespan(Heuristics.minMin(p));
        double mkMaxMin = p.makespan(Heuristics.maxMin(p));
        double bestHeur = Math.min(mkFcfs, Math.min(mkMinMin, mkMaxMin));

        System.out.printf("ANGGARAN ITERASI vs MUTU  |  %d task, %d VM, N=%d, %d run%n",
                nTasks, nVms, N_AGENTS, RUNS);
        System.out.printf("Ruang pencarian %d^%d. Batas bawah teoretis %.2f s.%n", nVms, nTasks, lb);
        System.out.printf("Heuristik  FCFS %.2f | MinMin %.2f | MaxMin %.2f  -> terbaik %.2f s%n%n",
                mkFcfs, mkMinMin, mkMaxMin, bestHeur);

        System.out.printf("%-8s %-14s %-14s %-14s %-14s %-12s%n",
                "T", "HHO", "HHO+seed", "PSO", "GA", "waktu HHO");
        System.out.println("-".repeat(80));

        StringBuilder csv = new StringBuilder(
            "tasks,vms,budget,algorithm,makespan_avg,makespan_std,vs_best_heuristic_pct,ms_avg\n");

        for (int T : BUDGETS) {
            double[] hho = new double[RUNS], seeded = new double[RUNS],
                     pso = new double[RUNS], ga = new double[RUNS];
            double[] hhoMs = new double[RUNS];

            for (int r = 0; r < RUNS; r++) {
                long seed = 500_000L + r;
                var cfg = new HarrisHawksOptimization.Config(N_AGENTS, T, seed);
                var res = new HarrisHawksOptimization(cfg).optimize(p);
                hho[r] = res.bestFitness; hhoMs[r] = res.elapsedMillis();

                seeded[r] = SeededHho.optimize(p, N_AGENTS, T, seed).bestFitness;
                pso[r] = new Baselines.Pso(N_AGENTS, T, seed).optimize(p).bestFitness;
                ga[r]  = new Baselines.Ga(N_AGENTS, T, seed).optimize(p).bestFitness;
            }

            System.out.printf("%-8d %-14.2f %-14.2f %-14.2f %-14.2f %-12.0f%n",
                    T, Statistics.mean(hho), Statistics.mean(seeded),
                    Statistics.mean(pso), Statistics.mean(ga), Statistics.mean(hhoMs));

            record Row(String alg, double[] v) {}
            for (Row row : new Row[]{ new Row("HHO", hho), new Row("HHO+seed", seeded),
                                      new Row("PSO", pso), new Row("GA", ga) }) {
                csv.append(String.format(Locale.US, "%d,%d,%d,%s,%.4f,%.4f,%.2f,%.3f%n",
                        nTasks, nVms, T, row.alg(), Statistics.mean(row.v()),
                        Statistics.std(row.v()),
                        100.0 * (Statistics.mean(row.v()) - bestHeur) / bestHeur,
                        Statistics.mean(hhoMs)));
            }
        }

        csv.append(String.format(Locale.US, "%d,%d,0,BestHeuristic,%.4f,0,0,0%n",
                nTasks, nVms, bestHeur));

        Path out = Paths.get("results");
        Files.createDirectories(out);
        try (PrintWriter w = new PrintWriter(
                Files.newBufferedWriter(out.resolve("convergence_budget.csv")))) {
            w.print(csv);
        }
        System.out.println("\nTersimpan: results/convergence_budget.csv");
    }

    private ConvergenceExperiment() {}
}
