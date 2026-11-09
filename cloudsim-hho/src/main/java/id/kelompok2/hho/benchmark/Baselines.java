package id.kelompok2.hho.benchmark;

import id.kelompok2.hho.core.*;

/**
 * Algoritma pembanding yang dipakai paper HHO maupun paper HHO-CloudSim:
 * PSO, GA, dan DE. Ketiganya memakai populasi dan anggaran iterasi yang SAMA
 * dengan HHO (N=30, T=500) supaya perbandingannya adil: beda hasil berasal
 * dari strategi pencarian, bukan dari anggaran komputasi yang timpang.
 */
public final class Baselines {

    private Baselines() {}

    private static void clamp(double[] x, double[] lb, double[] ub) {
        for (int d = 0; d < x.length; d++) {
            if (x[d] < lb[d]) x[d] = lb[d];
            else if (x[d] > ub[d]) x[d] = ub[d];
        }
    }

    // ------------------------------------------------------------------ PSO

    /** Particle Swarm Optimization, inertia menurun linier 0.9 -> 0.4, c1=c2=2. */
    public static final class Pso implements Optimizer {
        private final int n, t; private final long seed;
        public Pso(int n, int t, long seed) { this.n = n; this.t = t; this.seed = seed; }
        @Override public String name() { return "PSO"; }

        @Override public OptimizationResult optimize(ObjectiveFunction f) {
            final int dim = f.dimension();
            final double[] lb = f.lowerBound(), ub = f.upperBound();
            final Rng rng = new Rng(seed);
            long evals = 0; long t0 = System.nanoTime();

            double[][] x = new double[n][dim], v = new double[n][dim], pb = new double[n][dim];
            double[] pbf = new double[n];
            double[] gb = new double[dim]; double gbf = Double.POSITIVE_INFINITY;

            for (int i = 0; i < n; i++) {
                for (int d = 0; d < dim; d++) {
                    x[i][d] = lb[d] + rng.nextDouble() * (ub[d] - lb[d]);
                    v[i][d] = 0.0;
                }
                pbf[i] = f.evaluate(x[i]); evals++;
                pb[i] = x[i].clone();
                if (pbf[i] < gbf) { gbf = pbf[i]; gb = x[i].clone(); }
            }

            double[] curve = new double[t];
            for (int it = 0; it < t; it++) {
                double w = 0.9 - (0.9 - 0.4) * it / (double) t;
                for (int i = 0; i < n; i++) {
                    for (int d = 0; d < dim; d++) {
                        double r1 = rng.nextDouble(), r2 = rng.nextDouble();
                        v[i][d] = w * v[i][d]
                                + 2.0 * r1 * (pb[i][d] - x[i][d])
                                + 2.0 * r2 * (gb[d]    - x[i][d]);
                        // batasi kecepatan ke 20% rentang agar tidak meledak
                        double vmax = 0.2 * (ub[d] - lb[d]);
                        if (v[i][d] >  vmax) v[i][d] =  vmax;
                        if (v[i][d] < -vmax) v[i][d] = -vmax;
                        x[i][d] += v[i][d];
                    }
                    clamp(x[i], lb, ub);
                    double fit = f.evaluate(x[i]); evals++;
                    if (fit < pbf[i]) { pbf[i] = fit; pb[i] = x[i].clone(); }
                    if (fit < gbf)    { gbf = fit;    gb = x[i].clone(); }
                }
                curve[it] = gbf;
            }
            OptimizationResult r = new OptimizationResult();
            r.bestPosition = gb; r.bestFitness = gbf; r.convergenceCurve = curve;
            r.functionEvaluations = evals; r.elapsedNanos = System.nanoTime() - t0;
            return r;
        }
    }

    // ------------------------------------------------------------------- GA

    /** Genetic Algorithm: turnamen-2, crossover BLX-alpha, mutasi Gaussian, elitisme-1. */
    public static final class Ga implements Optimizer {
        private final int n, t; private final long seed;
        private final double pc = 0.8, pm = 0.05, alpha = 0.5;
        public Ga(int n, int t, long seed) { this.n = n; this.t = t; this.seed = seed; }
        @Override public String name() { return "GA"; }

        @Override public OptimizationResult optimize(ObjectiveFunction f) {
            final int dim = f.dimension();
            final double[] lb = f.lowerBound(), ub = f.upperBound();
            final Rng rng = new Rng(seed);
            long evals = 0; long t0 = System.nanoTime();

            double[][] pop = new double[n][dim];
            double[] fit = new double[n];
            for (int i = 0; i < n; i++) {
                for (int d = 0; d < dim; d++)
                    pop[i][d] = lb[d] + rng.nextDouble() * (ub[d] - lb[d]);
                fit[i] = f.evaluate(pop[i]); evals++;
            }
            int bi = argMin(fit);
            double[] best = pop[bi].clone(); double bestF = fit[bi];

            double[] curve = new double[t];
            for (int it = 0; it < t; it++) {
                double[][] next = new double[n][];
                double[] nextF = new double[n];
                next[0] = best.clone(); nextF[0] = bestF;     // elitisme

                for (int i = 1; i < n; i++) {
                    double[] p1 = pop[tournament(rng, fit)];
                    double[] p2 = pop[tournament(rng, fit)];
                    double[] ch = new double[dim];
                    for (int d = 0; d < dim; d++) {
                        if (rng.nextDouble() < pc) {
                            // BLX-alpha
                            double lo = Math.min(p1[d], p2[d]), hi = Math.max(p1[d], p2[d]);
                            double rg = hi - lo;
                            ch[d] = (lo - alpha * rg) + rng.nextDouble() * (rg * (1 + 2 * alpha));
                        } else {
                            ch[d] = p1[d];
                        }
                        if (rng.nextDouble() < pm)
                            ch[d] += rng.nextGaussian() * 0.1 * (ub[d] - lb[d]);
                    }
                    clamp(ch, lb, ub);
                    next[i] = ch; nextF[i] = f.evaluate(ch); evals++;
                    if (nextF[i] < bestF) { bestF = nextF[i]; best = ch.clone(); }
                }
                pop = next; fit = nextF;
                curve[it] = bestF;
            }
            OptimizationResult r = new OptimizationResult();
            r.bestPosition = best; r.bestFitness = bestF; r.convergenceCurve = curve;
            r.functionEvaluations = evals; r.elapsedNanos = System.nanoTime() - t0;
            return r;
        }

        private int tournament(Rng rng, double[] fit) {
            int a = rng.nextInt(fit.length), b = rng.nextInt(fit.length);
            return fit[a] <= fit[b] ? a : b;
        }
    }

    // ------------------------------------------------------------------- DE

    /** Differential Evolution DE/rand/1/bin, F=0.5, CR=0.9 (Storn & Price 1997). */
    public static final class De implements Optimizer {
        private final int n, t; private final long seed;
        private final double F = 0.5, CR = 0.9;
        public De(int n, int t, long seed) { this.n = n; this.t = t; this.seed = seed; }
        @Override public String name() { return "DE"; }

        @Override public OptimizationResult optimize(ObjectiveFunction f) {
            final int dim = f.dimension();
            final double[] lb = f.lowerBound(), ub = f.upperBound();
            final Rng rng = new Rng(seed);
            long evals = 0; long t0 = System.nanoTime();

            double[][] pop = new double[n][dim];
            double[] fit = new double[n];
            for (int i = 0; i < n; i++) {
                for (int d = 0; d < dim; d++)
                    pop[i][d] = lb[d] + rng.nextDouble() * (ub[d] - lb[d]);
                fit[i] = f.evaluate(pop[i]); evals++;
            }
            int bi = argMin(fit);
            double[] best = pop[bi].clone(); double bestF = fit[bi];

            double[] curve = new double[t];
            for (int it = 0; it < t; it++) {
                for (int i = 0; i < n; i++) {
                    int a, b, c;
                    do { a = rng.nextInt(n); } while (a == i);
                    do { b = rng.nextInt(n); } while (b == i || b == a);
                    do { c = rng.nextInt(n); } while (c == i || c == a || c == b);

                    int jr = rng.nextInt(dim);
                    double[] trial = new double[dim];
                    for (int d = 0; d < dim; d++) {
                        boolean take = rng.nextDouble() <= CR || d == jr;
                        trial[d] = take ? pop[a][d] + F * (pop[b][d] - pop[c][d]) : pop[i][d];
                    }
                    clamp(trial, lb, ub);
                    double ft = f.evaluate(trial); evals++;
                    if (ft <= fit[i]) {
                        pop[i] = trial; fit[i] = ft;
                        if (ft < bestF) { bestF = ft; best = trial.clone(); }
                    }
                }
                curve[it] = bestF;
            }
            OptimizationResult r = new OptimizationResult();
            r.bestPosition = best; r.bestFitness = bestF; r.convergenceCurve = curve;
            r.functionEvaluations = evals; r.elapsedNanos = System.nanoTime() - t0;
            return r;
        }
    }

    static int argMin(double[] a) {
        int k = 0;
        for (int i = 1; i < a.length; i++) if (a[i] < a[k]) k = i;
        return k;
    }
}
