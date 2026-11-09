package id.kelompok2.hho.core;

/**
 * Harris Hawks Optimization.
 *
 * Heidari, Mirjalili, Faris, Aljarah, Mafarja, Chen (2019),
 * "Harris hawks optimization: Algorithm and applications",
 * Future Generation Computer Systems 97, 849-872.
 *
 * Penomoran persamaan di komentar mengikuti paper tersebut. Alur dan urutan
 * pengacakan mengikuti HHO.m resmi penulis, karena angka yang dilaporkan paper
 * dihasilkan oleh kode itu. Dua titik selisih antara teks paper dan kode resmi
 * ditandai eksplisit dan dapat dipilih lewat Config.
 */
public final class HarrisHawksOptimization implements Optimizer {

    /** Pemetaan q pada fase eksplorasi, eq.(1). */
    public enum ExplorationMapping {
        /** HHO.m resmi: q lebih kecil dari 0.5 memakai X_rand. Bawaan. */
        MATLAB_CODE,
        /** Teks paper eq.(1): q lebih besar sama dengan 0.5 memakai X_rand. */
        PAPER_EQ1
    }

    public static final class Config {
        public int populationSize = 30;          // N pada paper
        public int maxIterations  = 500;         // T pada paper
        public long seed          = 1L;
        public LevyFlight.Variant levyVariant = LevyFlight.Variant.MATLAB_CODE;
        public ExplorationMapping explorationMapping = ExplorationMapping.MATLAB_CODE;
        /** Catat fase dan energi tiap iterasi (untuk pengujian). */
        public boolean trace = false;
        /**
         * Populasi awal kustom. null berarti acak seragam seperti paper.
         * Baris yang disediakan dipakai apa adanya; sisanya diisi acak.
         * Dipakai untuk menguji pengaruh inisialisasi terinformasi tanpa
         * mengubah satu pun persamaan HHO.
         */
        public double[][] initialPopulation = null;

        public Config() {}
        public Config(int n, int t, long seed) {
            this.populationSize = n; this.maxIterations = t; this.seed = seed;
        }
    }

    /** Strategi yang dipakai seekor hawk pada satu iterasi. */
    public enum Phase { EXPLORE, SOFT_BESIEGE, HARD_BESIEGE, SOFT_DIVE, HARD_DIVE }

    public static final class Result extends OptimizationResult {
        /** Hanya terisi bila Config.trace aktif. */
        public Phase[][] phaseTrace;
        public double[][] energyTrace;
    }

    private final Config cfg;

    public HarrisHawksOptimization(Config cfg) { this.cfg = cfg; }

    public Config config() { return cfg; }

    @Override public String name() { return "HHO"; }

    @Override public Result optimize(ObjectiveFunction f) {
        final int N = cfg.populationSize;
        final int T = cfg.maxIterations;
        final int dim = f.dimension();
        final double[] lb = f.lowerBound();
        final double[] ub = f.upperBound();
        final Rng rng = new Rng(cfg.seed);

        long evals = 0;
        final long t0 = System.nanoTime();

        // Inisialisasi populasi acak seragam dalam [lb, ub]; baris yang
        // disediakan lewat Config.initialPopulation dipakai apa adanya.
        double[][] X = new double[N][dim];
        final int seeded = (cfg.initialPopulation == null) ? 0
                         : Math.min(N, cfg.initialPopulation.length);
        for (int i = 0; i < seeded; i++) X[i] = cfg.initialPopulation[i].clone();
        for (int i = seeded; i < N; i++)
            for (int d = 0; d < dim; d++)
                X[i][d] = lb[d] + rng.nextDouble() * (ub[d] - lb[d]);

        double[] rabbit = new double[dim];
        double rabbitEnergy = Double.POSITIVE_INFINITY;   // fitness terbaik
        double[] curve = new double[T];

        Phase[][]  phaseTrace  = cfg.trace ? new Phase[T][N]  : null;
        double[][] energyTrace = cfg.trace ? new double[T][N] : null;

        for (int t = 0; t < T; t++) {

            // Batasi ke domain, evaluasi, perbarui kelinci (solusi terbaik)
            for (int i = 0; i < N; i++) {
                clampInPlace(X[i], lb, ub);
                double fit = f.evaluate(X[i]); evals++;
                if (fit < rabbitEnergy) {
                    rabbitEnergy = fit;
                    rabbit = X[i].clone();
                }
            }

            // eq.(3): E = 2 * E0 * (1 - t/T). E1 adalah faktor peluruhan.
            final double E1 = 2.0 * (1.0 - ((double) t / T));

            for (int i = 0; i < N; i++) {
                final double E0 = 2.0 * rng.nextDouble() - 1.0;   // E0 ~ U(-1,1)
                final double E  = E1 * E0;                        // energi kabur
                final double absE = Math.abs(E);
                Phase phase;

                if (absE >= 1.0) {
                    // FASE EKSPLORASI, eq.(1)
                    phase = Phase.EXPLORE;
                    final double q = rng.nextDouble();
                    final int randIdx = rng.nextInt(N);
                    final double[] Xrand = X[randIdx];

                    final boolean useRandomHawk = (cfg.explorationMapping
                            == ExplorationMapping.MATLAB_CODE) ? (q < 0.5) : (q >= 0.5);

                    if (useRandomHawk) {
                        // Bertengger dekat anggota kawanan lain:
                        //   X(t+1) = X_rand - r1 * |X_rand - 2*r2*X(t)|
                        final double r1 = rng.nextDouble();
                        final double r2 = rng.nextDouble();
                        for (int d = 0; d < dim; d++)
                            X[i][d] = Xrand[d] - r1 * Math.abs(Xrand[d] - 2.0 * r2 * X[i][d]);
                    } else {
                        // Bertengger di lokasi acak dalam jangkauan kawanan:
                        //   X(t+1) = (X_rabbit - X_m) - r3*(LB + r4*(UB-LB))
                        final double[] Xm = columnMean(X);
                        final double r3 = rng.nextDouble();
                        final double r4 = rng.nextDouble();
                        for (int d = 0; d < dim; d++)
                            X[i][d] = (rabbit[d] - Xm[d]) - r3 * (lb[d] + r4 * (ub[d] - lb[d]));
                    }

                } else {
                    // FASE EKSPLOITASI
                    final double r = rng.nextDouble();   // peluang kelinci kabur

                    if (r >= 0.5 && absE < 0.5) {
                        // eq.(6) HARD BESIEGE: X(t+1) = X_rabbit - E*|dX|
                        phase = Phase.HARD_BESIEGE;
                        for (int d = 0; d < dim; d++)
                            X[i][d] = rabbit[d] - E * Math.abs(rabbit[d] - X[i][d]);

                    } else if (r >= 0.5) {
                        // eq.(4) SOFT BESIEGE: X(t+1) = dX - E*|J*X_rabbit - X(t)|
                        // dengan dX = X_rabbit - X(t), eq.(5), dan J = 2(1-r5)
                        phase = Phase.SOFT_BESIEGE;
                        final double J = 2.0 * (1.0 - rng.nextDouble());
                        for (int d = 0; d < dim; d++)
                            X[i][d] = (rabbit[d] - X[i][d])
                                    - E * Math.abs(J * rabbit[d] - X[i][d]);

                    } else if (absE >= 0.5) {
                        // eq.(10) SOFT BESIEGE + PROGRESSIVE RAPID DIVES
                        phase = Phase.SOFT_DIVE;
                        final double J = 2.0 * (1.0 - rng.nextDouble());
                        final double fCur = f.evaluate(X[i]); evals++;

                        // eq.(7): Y = X_rabbit - E*|J*X_rabbit - X(t)|
                        double[] Y = new double[dim];
                        for (int d = 0; d < dim; d++)
                            Y[d] = rabbit[d] - E * Math.abs(J * rabbit[d] - X[i][d]);
                        clampInPlace(Y, lb, ub);
                        double fY = f.evaluate(Y); evals++;

                        if (fY < fCur) {
                            X[i] = Y;
                        } else {
                            // eq.(8): Z = Y + S x LF(D), S vektor acak U(0,1)
                            double[] lf = LevyFlight.step(dim, rng, cfg.levyVariant);
                            double[] Z = new double[dim];
                            for (int d = 0; d < dim; d++)
                                Z[d] = Y[d] + rng.nextDouble() * lf[d];
                            clampInPlace(Z, lb, ub);
                            double fZ = f.evaluate(Z); evals++;
                            if (fZ < fCur) X[i] = Z;
                        }

                    } else {
                        // eq.(11) HARD BESIEGE + PROGRESSIVE RAPID DIVES
                        // Sama seperti eq.(10) tetapi memakai X_m, bukan X(t).
                        phase = Phase.HARD_DIVE;
                        final double J = 2.0 * (1.0 - rng.nextDouble());
                        final double[] Xm = columnMean(X);
                        final double fCur = f.evaluate(X[i]); evals++;

                        double[] Y = new double[dim];
                        for (int d = 0; d < dim; d++)
                            Y[d] = rabbit[d] - E * Math.abs(J * rabbit[d] - Xm[d]);
                        clampInPlace(Y, lb, ub);
                        double fY = f.evaluate(Y); evals++;

                        if (fY < fCur) {
                            X[i] = Y;
                        } else {
                            double[] lf = LevyFlight.step(dim, rng, cfg.levyVariant);
                            double[] Z = new double[dim];
                            for (int d = 0; d < dim; d++)
                                Z[d] = Y[d] + rng.nextDouble() * lf[d];
                            clampInPlace(Z, lb, ub);
                            double fZ = f.evaluate(Z); evals++;
                            if (fZ < fCur) X[i] = Z;
                        }
                    }
                }

                if (cfg.trace) { phaseTrace[t][i] = phase; energyTrace[t][i] = E; }
            }

            curve[t] = rabbitEnergy;
        }

        Result res = new Result();
        res.bestPosition = rabbit;
        res.bestFitness = rabbitEnergy;
        res.convergenceCurve = curve;
        res.functionEvaluations = evals;
        res.elapsedNanos = System.nanoTime() - t0;
        res.phaseTrace = phaseTrace;
        res.energyTrace = energyTrace;
        return res;
    }

    /** Rata-rata posisi populasi, eq.(2): X_m(t) = (1/N) * sum X_i(t). */
    static double[] columnMean(double[][] X) {
        final int n = X.length, d = X[0].length;
        double[] m = new double[d];
        for (double[] row : X) for (int j = 0; j < d; j++) m[j] += row[j];
        for (int j = 0; j < d; j++) m[j] /= n;
        return m;
    }

    /** Kembalikan posisi ke dalam domain [lb, ub]. */
    static void clampInPlace(double[] x, double[] lb, double[] ub) {
        for (int d = 0; d < x.length; d++) {
            if (x[d] < lb[d]) x[d] = lb[d];
            else if (x[d] > ub[d]) x[d] = ub[d];
        }
    }

    @Override public String toString() {
        return "HHO(N=" + cfg.populationSize + ", T=" + cfg.maxIterations + ")";
    }
}
