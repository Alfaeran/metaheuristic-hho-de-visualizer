package id.kelompok2.hho.benchmark;

import id.kelompok2.hho.core.ObjectiveFunction;

/**
 * 23 fungsi benchmark klasik F1-F23 yang dipakai paper HHO
 * (Heidari dkk. 2019, Tabel 1-3), definisi mengikuti berkas resmi
 * Get_Functions_details.m milik penulis.
 *
 *   F1-F7   unimodal, dim 30      - menguji kemampuan EKSPLOITASI
 *   F8-F13  multimodal, dim 30    - menguji kemampuan EKSPLORASI / lolos optimum lokal
 *   F14-F23 multimodal dim tetap  - menguji keseimbangan keduanya
 *
 * knownOptimum() berisi optimum global ANALITIK, bukan angka yang dikutip dari
 * tabel paper. Dengan begitu verifikasi tidak bergantung pada ketepatan kutipan.
 */
public final class BenchmarkFunctions {

    private BenchmarkFunctions() {}

    /** Jumlah fungsi pada suite. */
    public static final int COUNT = 23;

    private static double[] fill(int n, double v) {
        double[] a = new double[n];
        java.util.Arrays.fill(a, v);
        return a;
    }

    /** Fungsi penalti u(x,a,k,m) yang dipakai F12 dan F13. */
    static double u(double x, double a, double k, double m) {
        if (x > a)  return k * Math.pow(x - a, m);
        if (x < -a) return k * Math.pow(-x - a, m);
        return 0.0;
    }

    /** Kerangka fungsi berdimensi bebas dengan batas seragam. */
    private abstract static class Uniform implements ObjectiveFunction {
        private final String name; private final int dim;
        private final double lo, hi, opt;
        Uniform(String name, int dim, double lo, double hi, double opt) {
            this.name = name; this.dim = dim; this.lo = lo; this.hi = hi; this.opt = opt;
        }
        @Override public int dimension() { return dim; }
        @Override public double[] lowerBound() { return fill(dim, lo); }
        @Override public double[] upperBound() { return fill(dim, hi); }
        @Override public String name() { return name; }
        @Override public double knownOptimum() { return opt; }
        @Override public String toString() { return name; }
    }

    /**
     * Ambil fungsi ke-id (1..23). Parameter dim hanya dipakai F1-F13;
     * F14-F23 berdimensi tetap sesuai definisinya.
     */
    public static ObjectiveFunction get(int id, int dim) {
        switch (id) {
            case 1: return new Uniform("F1 Sphere", dim, -100, 100, 0) {
                public double evaluate(double[] x) {
                    double s = 0; for (double v : x) s += v * v; return s; } };

            case 2: return new Uniform("F2 Schwefel 2.22", dim, -10, 10, 0) {
                public double evaluate(double[] x) {
                    double s = 0, p = 1;
                    for (double v : x) { s += Math.abs(v); p *= Math.abs(v); }
                    return s + p; } };

            case 3: return new Uniform("F3 Schwefel 1.2", dim, -100, 100, 0) {
                public double evaluate(double[] x) {
                    double t = 0;
                    for (int i = 0; i < x.length; i++) {
                        double inner = 0;
                        for (int j = 0; j <= i; j++) inner += x[j];
                        t += inner * inner;
                    }
                    return t; } };

            case 4: return new Uniform("F4 Schwefel 2.21", dim, -100, 100, 0) {
                public double evaluate(double[] x) {
                    double m = 0; for (double v : x) m = Math.max(m, Math.abs(v)); return m; } };

            case 5: return new Uniform("F5 Rosenbrock", dim, -30, 30, 0) {
                public double evaluate(double[] x) {
                    double s = 0;
                    for (int i = 0; i < x.length - 1; i++) {
                        double a = x[i + 1] - x[i] * x[i], b = x[i] - 1.0;
                        s += 100.0 * a * a + b * b;
                    }
                    return s; } };

            case 6: return new Uniform("F6 Step", dim, -100, 100, 0) {
                public double evaluate(double[] x) {
                    double s = 0;
                    for (double v : x) { double t = v + 0.5; s += t * t; }
                    return s; } };

            case 7: return new Uniform("F7 Quartic+noise", dim, -1.28, 1.28, 0) {
                public double evaluate(double[] x) {
                    double s = 0;
                    for (int i = 0; i < x.length; i++) s += (i + 1) * Math.pow(x[i], 4);
                    return s + Math.random(); } };

            case 8: return new Uniform("F8 Schwefel", dim, -500, 500,
                                       -418.9829 * dim) {
                public double evaluate(double[] x) {
                    double s = 0;
                    for (double v : x) s += -v * Math.sin(Math.sqrt(Math.abs(v)));
                    return s; } };

            case 9: return new Uniform("F9 Rastrigin", dim, -5.12, 5.12, 0) {
                public double evaluate(double[] x) {
                    double s = 0;
                    for (double v : x) s += v * v - 10.0 * Math.cos(2.0 * Math.PI * v);
                    return s + 10.0 * x.length; } };

            case 10: return new Uniform("F10 Ackley", dim, -32, 32, 0) {
                public double evaluate(double[] x) {
                    int n = x.length; double s1 = 0, s2 = 0;
                    for (double v : x) { s1 += v * v; s2 += Math.cos(2.0 * Math.PI * v); }
                    return -20.0 * Math.exp(-0.2 * Math.sqrt(s1 / n))
                           - Math.exp(s2 / n) + 20.0 + Math.E; } };

            case 11: return new Uniform("F11 Griewank", dim, -600, 600, 0) {
                public double evaluate(double[] x) {
                    double s = 0, p = 1;
                    for (int i = 0; i < x.length; i++) {
                        s += x[i] * x[i];
                        p *= Math.cos(x[i] / Math.sqrt(i + 1.0));
                    }
                    return s / 4000.0 - p + 1.0; } };

            case 12: return new Uniform("F12 Penalized 1", dim, -50, 50, 0) {
                public double evaluate(double[] x) {
                    int n = x.length;
                    double[] y = new double[n];
                    for (int i = 0; i < n; i++) y[i] = 1.0 + (x[i] + 1.0) / 4.0;
                    double s = 0;
                    for (int i = 0; i < n - 1; i++) {
                        double a = y[i] - 1.0;
                        double b = 1.0 + 10.0 * Math.pow(Math.sin(Math.PI * y[i + 1]), 2);
                        s += a * a * b;
                    }
                    double last = y[n - 1] - 1.0;
                    double head = 10.0 * Math.pow(Math.sin(Math.PI * y[0]), 2);
                    double pen = 0;
                    for (double v : x) pen += u(v, 10, 100, 4);
                    return (Math.PI / n) * (head + s + last * last) + pen; } };

            case 13: return new Uniform("F13 Penalized 2", dim, -50, 50, 0) {
                public double evaluate(double[] x) {
                    int n = x.length;
                    double s = 0;
                    for (int i = 0; i < n - 1; i++) {
                        double a = x[i] - 1.0;
                        double b = 1.0 + Math.pow(Math.sin(3.0 * Math.PI * x[i + 1]), 2);
                        s += a * a * b;
                    }
                    double head = Math.pow(Math.sin(3.0 * Math.PI * x[0]), 2);
                    double lastA = x[n - 1] - 1.0;
                    double lastB = 1.0 + Math.pow(Math.sin(2.0 * Math.PI * x[n - 1]), 2);
                    double pen = 0;
                    for (double v : x) pen += u(v, 5, 100, 4);
                    return 0.1 * (head + s + lastA * lastA * lastB) + pen; } };

            case 14: return new Uniform("F14 Shekel Foxholes", 2, -65.536, 65.536, 0.998003837794449) {
                public double evaluate(double[] x) {
                    final double[][] a = {
                      {-32,-16,0,16,32,-32,-16,0,16,32,-32,-16,0,16,32,-32,-16,0,16,32,-32,-16,0,16,32},
                      {-32,-32,-32,-32,-32,-16,-16,-16,-16,-16,0,0,0,0,0,16,16,16,16,16,32,32,32,32,32}};
                    double s = 0;
                    for (int j = 0; j < 25; j++) {
                        double t = 0;
                        for (int i = 0; i < 2; i++) t += Math.pow(x[i] - a[i][j], 6);
                        s += 1.0 / (j + 1.0 + t);
                    }
                    return Math.pow(1.0 / 500.0 + s, -1); } };

            case 15: return new Uniform("F15 Kowalik", 4, -5, 5, 3.0748598e-4) {
                public double evaluate(double[] x) {
                    final double[] aK = {.1957,.1947,.1735,.16,.0844,.0627,.0456,.0342,.0323,.0235,.0246};
                    final double[] bInv = {.25,.5,1,2,4,6,8,10,12,14,16};
                    double s = 0;
                    for (int i = 0; i < 11; i++) {
                        double b = 1.0 / bInv[i];
                        double num = x[0] * (b * b + x[1] * b);
                        double den = b * b + x[2] * b + x[3];
                        double t = aK[i] - num / den;
                        s += t * t;
                    }
                    return s; } };

            case 16: return new Uniform("F16 Six-hump camel", 2, -5, 5, -1.0316284534898774) {
                public double evaluate(double[] x) {
                    return 4 * x[0] * x[0] - 2.1 * Math.pow(x[0], 4) + Math.pow(x[0], 6) / 3.0
                         + x[0] * x[1] - 4 * x[1] * x[1] + 4 * Math.pow(x[1], 4); } };

            case 17: return new ObjectiveFunction() {
                public double evaluate(double[] x) {
                    double a = x[1] - 5.1 / (4 * Math.PI * Math.PI) * x[0] * x[0]
                             + 5.0 / Math.PI * x[0] - 6.0;
                    return a * a + 10.0 * (1 - 1.0 / (8 * Math.PI)) * Math.cos(x[0]) + 10.0; }
                public int dimension() { return 2; }
                // F17 adalah satu-satunya fungsi dengan batas berbeda per dimensi
                public double[] lowerBound() { return new double[]{-5, 0}; }
                public double[] upperBound() { return new double[]{10, 15}; }
                public String name() { return "F17 Branin"; }
                public double knownOptimum() { return 0.39788735772973816; }
                @Override public String toString() { return name(); } };

            // Berkas resmi Get_Functions_details.m memakai [-5,5] untuk F18,
            // bukan [-2,2] yang lazim di literatur lain. Optimum global 3
            // di (0,-1) tetap berada di dalam domain ini.
            case 18: return new Uniform("F18 Goldstein-Price", 2, -5, 5, 3.0) {
                public double evaluate(double[] x) {
                    double a = 1 + Math.pow(x[0] + x[1] + 1, 2)
                        * (19 - 14*x[0] + 3*x[0]*x[0] - 14*x[1] + 6*x[0]*x[1] + 3*x[1]*x[1]);
                    double b = 30 + Math.pow(2*x[0] - 3*x[1], 2)
                        * (18 - 32*x[0] + 12*x[0]*x[0] + 48*x[1] - 36*x[0]*x[1] + 27*x[1]*x[1]);
                    return a * b; } };

            case 19: return new Uniform("F19 Hartman 3-D", 3, 0, 1, -3.86278214782076) {
                public double evaluate(double[] x) {
                    final double[][] a = {{3,10,30},{.1,10,35},{3,10,30},{.1,10,35}};
                    final double[] c = {1,1.2,3,3.2};
                    final double[][] p = {{.3689,.117,.2673},{.4699,.4387,.747},
                                          {.1091,.8732,.5547},{.03815,.5743,.8828}};
                    double o = 0;
                    for (int i = 0; i < 4; i++) {
                        double s = 0;
                        for (int j = 0; j < 3; j++) s += a[i][j] * Math.pow(x[j] - p[i][j], 2);
                        o -= c[i] * Math.exp(-s);
                    }
                    return o; } };

            case 20: return new Uniform("F20 Hartman 6-D", 6, 0, 1, -3.32199517011271) {
                public double evaluate(double[] x) {
                    final double[][] a = {{10,3,17,3.5,1.7,8},{.05,10,17,.1,8,14},
                                          {3,3.5,1.7,10,17,8},{17,8,.05,10,.1,14}};
                    final double[] c = {1,1.2,3,3.2};
                    final double[][] p = {{.1312,.1696,.5569,.0124,.8283,.5886},
                                          {.2329,.4135,.8307,.3736,.1004,.9991},
                                          {.2348,.1415,.3522,.2883,.3047,.6650},
                                          {.4047,.8828,.8732,.5743,.1091,.0381}};
                    double o = 0;
                    for (int i = 0; i < 4; i++) {
                        double s = 0;
                        for (int j = 0; j < 6; j++) s += a[i][j] * Math.pow(x[j] - p[i][j], 2);
                        o -= c[i] * Math.exp(-s);
                    }
                    return o; } };

            case 21: return shekel(5,  "F21 Shekel m=5",  -10.1531996790582);
            case 22: return shekel(7,  "F22 Shekel m=7",  -10.4029405668187);
            case 23: return shekel(10, "F23 Shekel m=10", -10.5364098166920);

            default:
                throw new IllegalArgumentException("id fungsi harus 1..23, diberi " + id);
        }
    }

    /** Keluarga Shekel F21-F23, hanya berbeda jumlah suku m. */
    private static ObjectiveFunction shekel(final int m, String name, double opt) {
        return new Uniform(name, 4, 0, 10, opt) {
            public double evaluate(double[] x) {
                final double[][] a = {{4,4,4,4},{1,1,1,1},{8,8,8,8},{6,6,6,6},{3,7,3,7},
                                      {2,9,2,9},{5,5,3,3},{8,1,8,1},{6,2,6,2},{7,3.6,7,3.6}};
                final double[] c = {.1,.2,.2,.4,.4,.6,.3,.7,.5,.5};
                double o = 0;
                for (int i = 0; i < m; i++) {
                    double s = 0;
                    for (int j = 0; j < 4; j++) s += Math.pow(x[j] - a[i][j], 2);
                    o -= 1.0 / (s + c[i]);
                }
                return o; } };
    }

    /** Semua 23 fungsi dengan dimensi dim untuk F1-F13. */
    public static ObjectiveFunction[] all(int dim) {
        ObjectiveFunction[] fs = new ObjectiveFunction[COUNT];
        for (int i = 1; i <= COUNT; i++) fs[i - 1] = get(i, dim);
        return fs;
    }

    /** Kelompok fungsi, dipakai saat melaporkan hasil. */
    public static String group(int id) {
        if (id <= 7)  return "unimodal";
        if (id <= 13) return "multimodal";
        return "multimodal-fixed-dim";
    }
}
