package id.kelompok2.hho;

import id.kelompok2.hho.core.*;
import id.kelompok2.hho.benchmark.BenchmarkFunctions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TEST CASE: membuktikan implementasi HHO sesuai persamaan pada
 * Heidari dkk. (2019), bukan sekadar "menghasilkan angka kecil".
 *
 * Tiap test menguji satu invarian yang dapat diturunkan dari paper, sehingga
 * kegagalannya menunjuk persamaan tertentu, bukan sekadar "hasil jelek".
 */
class HhoCorrectnessTest {

    // ---------------------------------------------------------------- eq.(3)

    @Test
    @DisplayName("eq.3 - |E| tidak pernah melebihi 2(1-t/T)")
    void energyNeverExceedsDecayEnvelope() {
        var cfg = new HarrisHawksOptimization.Config(30, 200, 42L);
        cfg.trace = true;
        var res = new HarrisHawksOptimization(cfg).optimize(BenchmarkFunctions.get(1, 10));

        for (int t = 0; t < res.energyTrace.length; t++) {
            double envelope = 2.0 * (1.0 - (double) t / res.energyTrace.length);
            for (double e : res.energyTrace[t]) {
                assertTrue(Math.abs(e) <= envelope + 1e-12,
                    String.format("t=%d: |E|=%.6f melebihi selubung %.6f", t, Math.abs(e), envelope));
            }
        }
    }

    @Test
    @DisplayName("eq.3 - pada t/T=0.9 selubung |E| hanya 0.2 sehingga fase pasti eksploitasi")
    void atNinetyPercentProgressAllHawksExploit() {
        final int T = 500;
        var cfg = new HarrisHawksOptimization.Config(30, T, 7L);
        cfg.trace = true;
        var res = new HarrisHawksOptimization(cfg).optimize(BenchmarkFunctions.get(1, 10));

        int t = (int) (0.9 * T);
        double envelope = 2.0 * (1.0 - (double) t / T);
        assertEquals(0.2, envelope, 1e-12, "selubung di t/T=0.9 harus tepat 0.2");

        for (int i = 0; i < res.energyTrace[t].length; i++) {
            assertTrue(Math.abs(res.energyTrace[t][i]) < 1.0,
                "pada t/T=0.9 tidak boleh ada |E|>=1");
            assertNotEquals(HarrisHawksOptimization.Phase.EXPLORE, res.phaseTrace[t][i],
                "pada t/T=0.9 tidak boleh ada hawk yang masih menjelajah");
        }
    }

    @Test
    @DisplayName("eq.3 - fase EXPLORE hanya muncul saat |E|>=1, eksploitasi hanya saat |E|<1")
    void phaseMatchesEnergyCondition() {
        var cfg = new HarrisHawksOptimization.Config(30, 300, 11L);
        cfg.trace = true;
        var res = new HarrisHawksOptimization(cfg).optimize(BenchmarkFunctions.get(9, 10));

        for (int t = 0; t < res.phaseTrace.length; t++) {
            for (int i = 0; i < res.phaseTrace[t].length; i++) {
                double absE = Math.abs(res.energyTrace[t][i]);
                var ph = res.phaseTrace[t][i];
                if (absE >= 1.0) {
                    assertEquals(HarrisHawksOptimization.Phase.EXPLORE, ph);
                } else {
                    assertNotEquals(HarrisHawksOptimization.Phase.EXPLORE, ph);
                    // besiege keras hanya saat |E|<0.5, lunak hanya saat |E|>=0.5
                    if (ph == HarrisHawksOptimization.Phase.SOFT_BESIEGE
                     || ph == HarrisHawksOptimization.Phase.SOFT_DIVE) {
                        assertTrue(absE >= 0.5, "fase lunak menuntut |E|>=0.5, dapat " + absE);
                    }
                    if (ph == HarrisHawksOptimization.Phase.HARD_BESIEGE
                     || ph == HarrisHawksOptimization.Phase.HARD_DIVE) {
                        assertTrue(absE < 0.5, "fase keras menuntut |E|<0.5, dapat " + absE);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- eq.(9)

    @Test
    @DisplayName("eq.9 - sigma Mantegna untuk beta=1.5 bernilai 0.6965745")
    void levySigmaMatchesMantegnaFormula() {
        // Dihitung manual dari G(2.5)=1.329340388, G(1.25)=0.906402477:
        //   num = 1.329340388 * sin(0.75*pi) = 0.939950...
        //   den = 0.906402477 * 1.5 * 2^0.25 = 1.616803...
        //   sigma = (num/den)^(1/1.5) = 0.6965745...
        assertEquals(0.69657454, LevyFlight.SIGMA, 1e-7);
    }

    @Test
    @DisplayName("eq.9 - Gamma(n) = (n-1)! pada bilangan bulat")
    void gammaMatchesFactorial() {
        assertEquals(1.0,   LevyFlight.gamma(1), 1e-10);
        assertEquals(1.0,   LevyFlight.gamma(2), 1e-10);
        assertEquals(2.0,   LevyFlight.gamma(3), 1e-9);
        assertEquals(6.0,   LevyFlight.gamma(4), 1e-8);
        assertEquals(120.0, LevyFlight.gamma(6), 1e-6);
        // Gamma(1/2) = sqrt(pi)
        assertEquals(Math.sqrt(Math.PI), LevyFlight.gamma(0.5), 1e-10);
    }

    @Test
    @DisplayName("eq.9 - varian paper tepat 100x lebih kecil dari varian kode resmi")
    void paperVariantIsExactlyHundredthOfCodeVariant() {
        // Selisih nyata antara teks paper (ada faktor 0.01) dan HHO.m resmi
        // (tanpa faktor itu). Diuji, bukan diasumsikan.
        double[] a = LevyFlight.step(50, new Rng(5L), LevyFlight.Variant.MATLAB_CODE);
        double[] b = LevyFlight.step(50, new Rng(5L), LevyFlight.Variant.PAPER_EQ9);
        for (int i = 0; i < a.length; i++) {
            assertEquals(a[i] * 0.01, b[i], Math.abs(a[i]) * 1e-12 + 1e-300);
        }
    }

    // ------------------------------------------------- domain dan determinisme

    @Test
    @DisplayName("Solusi terbaik selalu berada di dalam domain [lb,ub]")
    void bestSolutionStaysInsideBounds() {
        for (int id = 1; id <= 23; id++) {
            var f = BenchmarkFunctions.get(id, 10);
            var cfg = new HarrisHawksOptimization.Config(20, 60, 100L + id);
            var res = new HarrisHawksOptimization(cfg).optimize(f);
            double[] lb = f.lowerBound(), ub = f.upperBound();
            for (int d = 0; d < f.dimension(); d++) {
                assertTrue(res.bestPosition[d] >= lb[d] - 1e-9
                        && res.bestPosition[d] <= ub[d] + 1e-9,
                    f.name() + " dimensi " + d + " keluar domain: " + res.bestPosition[d]);
            }
        }
    }

    @Test
    @DisplayName("Seed sama menghasilkan hasil identik, seed beda menghasilkan hasil berbeda")
    void deterministicUnderSameSeed() {
        var f = BenchmarkFunctions.get(9, 20);   // Rastrigin, tanpa noise
        var a = new HarrisHawksOptimization(new HarrisHawksOptimization.Config(30, 100, 2024L)).optimize(f);
        var b = new HarrisHawksOptimization(new HarrisHawksOptimization.Config(30, 100, 2024L)).optimize(f);
        var c = new HarrisHawksOptimization(new HarrisHawksOptimization.Config(30, 100, 2025L)).optimize(f);

        assertEquals(a.bestFitness, b.bestFitness, 0.0, "seed sama harus identik bit-per-bit");
        assertArrayEquals(a.bestPosition, b.bestPosition, 0.0);
        assertArrayEquals(a.convergenceCurve, b.convergenceCurve, 0.0);

        // Seed berbeda dibandingkan pada ITERASI PERTAMA, bukan nilai akhir:
        // dua jalur acak yang berbeda bisa sama-sama mencapai optimum eksak
        // (pada Rastrigin keduanya mencapai 0.0), sehingga nilai akhir yang
        // sama bukan tanda determinisme yang salah.
        assertNotEquals(a.convergenceCurve[0], c.convergenceCurve[0],
            "populasi awal dari seed berbeda seharusnya berbeda");
    }

    @Test
    @DisplayName("Kurva konvergensi monoton tidak naik (kelinci hanya diganti bila lebih baik)")
    void convergenceCurveIsNonIncreasing() {
        var res = new HarrisHawksOptimization(
            new HarrisHawksOptimization.Config(30, 300, 77L))
            .optimize(BenchmarkFunctions.get(5, 20));   // Rosenbrock
        for (int t = 1; t < res.convergenceCurve.length; t++) {
            assertTrue(res.convergenceCurve[t] <= res.convergenceCurve[t - 1] + 1e-12,
                "kurva naik di iterasi " + t);
        }
    }

    // ------------------------------------------------- kebenaran fungsi uji

    @Test
    @DisplayName("F1-F13 bernilai optimum analitik tepat di titik optimumnya")
    void benchmarkFunctionsHitTheirKnownOptimum() {
        // Sphere, Rastrigin, Ackley, Griewank, Step: semuanya 0 di titik nol.
        for (int id : new int[]{1, 2, 3, 4, 9, 10, 11}) {
            var f = BenchmarkFunctions.get(id, 30);
            double v = f.evaluate(new double[30]);
            assertEquals(f.knownOptimum(), v, 1e-9, f.name() + " di titik nol");
        }
        // Rosenbrock: 0 di vektor satu.
        var f5 = BenchmarkFunctions.get(5, 30);
        double[] ones = new double[30];
        java.util.Arrays.fill(ones, 1.0);
        assertEquals(0.0, f5.evaluate(ones), 1e-9);

        // F6 Step pada berkas resmi adalah sum (x_i + 0.5)^2 TANPA floor, sehingga
        // optimumnya di x_i = -0.5, bukan di titik nol. Di titik nol nilainya
        // d*0.25 (= 7.5 untuk d=30), dan itu memang benar.
        var f6 = BenchmarkFunctions.get(6, 30);
        double[] negHalf = new double[30];
        java.util.Arrays.fill(negHalf, -0.5);
        assertEquals(0.0, f6.evaluate(negHalf), 1e-12, "F6 minimum di x=-0.5");
        assertEquals(7.5, f6.evaluate(new double[30]), 1e-12, "F6 di titik nol = d*0.25");

        // Schwefel F8: minimum -418.9829*d di x_i = 420.9687
        var f8 = BenchmarkFunctions.get(8, 30);
        double[] s = new double[30];
        java.util.Arrays.fill(s, 420.9687);
        assertEquals(f8.knownOptimum(), f8.evaluate(s), 1e-2);
    }

    @Test
    @DisplayName("F14-F23 bernilai optimum analitik di titik optimum yang diketahui")
    void fixedDimFunctionsHitKnownOptimum() {
        assertEquals(-1.0316284534898774,
            BenchmarkFunctions.get(16, 2).evaluate(new double[]{0.0898, -0.7126}), 1e-6);
        assertEquals(0.39788735772973816,
            BenchmarkFunctions.get(17, 2).evaluate(new double[]{Math.PI, 2.275}), 1e-6);
        assertEquals(3.0,
            BenchmarkFunctions.get(18, 2).evaluate(new double[]{0.0, -1.0}), 1e-9);
        assertEquals(-3.86278214782076,
            BenchmarkFunctions.get(19, 3).evaluate(new double[]{0.114614, 0.555649, 0.852547}), 1e-5);
        assertEquals(-10.1531996790582,
            BenchmarkFunctions.get(21, 4).evaluate(new double[]{4, 4, 4, 4}), 1e-3);
    }

    // ------------------------------------------------- konvergensi fungsional

    @Test
    @DisplayName("HHO mendekati optimum global pada Sphere unimodal (eksploitasi)")
    void convergesOnUnimodalSphere() {
        var res = new HarrisHawksOptimization(
            new HarrisHawksOptimization.Config(30, 500, 1L))
            .optimize(BenchmarkFunctions.get(1, 30));
        // Paper melaporkan HHO mencapai orde 1e-90an pada F1. Ambang longgar
        // 1e-20 cukup membuktikan konvergensi tanpa menuntut angka persis.
        assertTrue(res.bestFitness < 1e-20,
            "F1 seharusnya konvergen jauh di bawah 1e-20, dapat " + res.bestFitness);
    }

    @Test
    @DisplayName("HHO lolos dari optimum lokal pada Rastrigin multimodal (eksplorasi)")
    void escapesLocalOptimaOnRastrigin() {
        var res = new HarrisHawksOptimization(
            new HarrisHawksOptimization.Config(30, 500, 3L))
            .optimize(BenchmarkFunctions.get(9, 30));
        // Rastrigin dim 30 punya ribuan optimum lokal; nilai acak awal ~300.
        assertTrue(res.bestFitness < 1e-6,
            "F9 seharusnya mendekati 0, dapat " + res.bestFitness);
    }

    @Test
    @DisplayName("Jumlah evaluasi fungsi sesuai struktur algoritma")
    void functionEvaluationCountIsConsistent() {
        int N = 30, T = 100;
        var res = new HarrisHawksOptimization(
            new HarrisHawksOptimization.Config(N, T, 9L))
            .optimize(BenchmarkFunctions.get(1, 10));
        // Minimum N*T (evaluasi awal tiap iterasi). Fase dive menambah 2-3
        // evaluasi per hawk, jadi batas atas N*T*4.
        assertTrue(res.functionEvaluations >= (long) N * T,
            "minimal N*T evaluasi, dapat " + res.functionEvaluations);
        assertTrue(res.functionEvaluations <= (long) N * T * 4L,
            "tidak wajar melebihi 4*N*T, dapat " + res.functionEvaluations);
    }
}
