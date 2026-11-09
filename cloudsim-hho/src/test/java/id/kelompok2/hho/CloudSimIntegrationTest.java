package id.kelompok2.hho;

import id.kelompok2.hho.scheduling.*;
import id.kelompok2.hho.sim.CloudSimScenario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TEST CASE: membuktikan simulasi CloudSim benar-benar berjalan dan bahwa
 * model waktu pada fungsi objektif sepadan dengan hasil simulasi.
 */
class CloudSimIntegrationTest {

    @Test
    @DisplayName("Decoder random key selalu menghasilkan jadwal yang sah")
    void decoderAlwaysProducesValidSchedule() {
        var p = CloudSimScenario.problem(50, 8, 1L);
        var rng = new id.kelompok2.hho.core.Rng(99L);
        for (int trial = 0; trial < 500; trial++) {
            double[] x = new double[p.taskCount()];
            for (int i = 0; i < x.length; i++) x[i] = rng.nextDouble() * p.vmCount();
            int[] a = p.decode(x);
            assertEquals(p.taskCount(), a.length, "setiap task harus punya tepat satu penugasan");
            for (int v : a) assertTrue(v >= 0 && v < p.vmCount(), "indeks VM di luar jangkauan: " + v);
        }
        // Termasuk nilai di tepi domain
        double[] edge = new double[p.taskCount()];
        java.util.Arrays.fill(edge, p.vmCount());        // tepat M
        for (int v : p.decode(edge)) assertEquals(p.vmCount() - 1, v);
        java.util.Arrays.fill(edge, -1.0);               // di bawah domain
        for (int v : p.decode(edge)) assertEquals(0, v);
    }

    @Test
    @DisplayName("Makespan = waktu selesai VM terbesar")
    void makespanEqualsMaxVmCompletion() {
        var p = new SchedulingProblem(new double[]{1000, 2000, 3000}, new double[]{100, 200});
        int[] a = {0, 1, 0};                 // VM0: 1000+3000=4000 MI @100 = 40 s
        double[] fin = p.completionTimes(a); // VM1: 2000 MI @200 = 10 s
        assertEquals(40.0, fin[0], 1e-9);
        assertEquals(10.0, fin[1], 1e-9);
        assertEquals(40.0, p.makespan(a), 1e-9);
    }

    @Test
    @DisplayName("Heuristik menghasilkan jadwal sah dan makespan di atas batas bawah teoretis")
    void heuristicsAreValidAndAboveLowerBound() {
        var p = CloudSimScenario.problem(200, 20, 7L);
        double lb = p.lowerBoundMakespan();
        for (var entry : java.util.Map.of(
                "FCFS",   Heuristics.fcfs(p),
                "SJF",    Heuristics.sjf(p),
                "RR",     Heuristics.roundRobin(p),
                "MinMin", Heuristics.minMin(p),
                "MaxMin", Heuristics.maxMin(p)).entrySet()) {
            int[] a = entry.getValue();
            assertEquals(p.taskCount(), a.length, entry.getKey());
            for (int v : a) assertTrue(v >= 0 && v < p.vmCount());
            assertTrue(p.makespan(a) >= lb - 1e-6,
                entry.getKey() + " makespan mustahil di bawah batas bawah teoretis");
        }
    }

    @Test
    @DisplayName("CloudSim menjalankan seluruh cloudlet sampai selesai")
    void cloudSimCompletesAllCloudlets() {
        var p = CloudSimScenario.problem(100, 20, 3L);
        int[] a = Heuristics.minMin(p);
        var out = CloudSimScenario.run(p.taskLengths(), p.vmMipsArray(), a);

        assertEquals(100, out.finishedCloudlets, "semua cloudlet harus selesai");
        assertTrue(out.makespan > 0, "makespan harus positif");
        assertTrue(out.throughput > 0, "throughput harus positif");
        assertTrue(out.avgVmUtilization > 0 && out.avgVmUtilization <= 1.0 + 1e-9,
            "utilisasi harus di (0,1], dapat " + out.avgVmUtilization);
        assertTrue(out.degreeOfImbalance >= 0, "degree of imbalance tidak boleh negatif");
    }

    @Test
    @DisplayName("Makespan CloudSim sepadan dengan model waktu fungsi objektif")
    void cloudSimMakespanAgreesWithObjectiveModel() {
        var p = CloudSimScenario.problem(80, 10, 5L);
        int[] a = Heuristics.minMin(p);
        double modelled = p.makespan(a);
        var out = CloudSimScenario.run(p.taskLengths(), p.vmMipsArray(), a);

        // Simulasi menambah overhead (transfer berkas, penjadwalan), sehingga
        // tidak akan sama persis. Yang harus benar adalah ORDE dan URUTANNYA:
        // selisih relatif wajar di bawah 10%.
        double rel = Math.abs(out.makespan - modelled) / modelled;
        assertTrue(rel < 0.10,
            String.format("model %.2f s vs CloudSim %.2f s, selisih relatif %.1f%%",
                modelled, out.makespan, rel * 100));
    }

    @Test
    @DisplayName("Jadwal lebih baik menurut model juga lebih baik di CloudSim")
    void betterModelScheduleIsBetterInCloudSim() {
        var p = CloudSimScenario.problem(150, 12, 11L);
        int[] good = Heuristics.minMin(p);
        int[] bad  = Heuristics.roundRobin(p);
        assertTrue(p.makespan(good) < p.makespan(bad), "prasyarat: MinMin lebih baik di model");

        double mkGood = CloudSimScenario.run(p.taskLengths(), p.vmMipsArray(), good).makespan;
        double mkBad  = CloudSimScenario.run(p.taskLengths(), p.vmMipsArray(), bad).makespan;
        assertTrue(mkGood < mkBad,
            String.format("urutan harus terjaga di CloudSim: MinMin %.2f vs RR %.2f", mkGood, mkBad));
    }
}
