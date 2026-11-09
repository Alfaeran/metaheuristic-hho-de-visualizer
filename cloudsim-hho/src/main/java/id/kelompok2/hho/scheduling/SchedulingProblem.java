package id.kelompok2.hho.scheduling;

import id.kelompok2.hho.core.ObjectiveFunction;

/**
 * Pemetaan masalah penjadwalan task cloud ke ruang kontinu yang dapat dicari
 * oleh HHO, PSO, GA, dan DE.
 *
 * ENCODING RANDOM KEY
 * Vektor solusi x berdimensi N (jumlah task), setiap x_i berada di [0, M)
 * dengan M jumlah VM. Task i ditugaskan ke VM floor(x_i). Inilah jembatan
 * antara metaheuristik yang bekerja di ruang KONTINU dengan penjadwalan yang
 * bersifat DISKRET. Encoding ini selalu menghasilkan jadwal yang sah, sehingga
 * tidak perlu prosedur perbaikan (repair) yang dapat membiaskan perbandingan.
 *
 * MODEL WAKTU
 * Waktu eksekusi task i pada VM j = panjang_i (MI) / kapasitas_j (MIPS).
 * Task pada satu VM dieksekusi berurutan (space-shared), sehingga waktu
 * selesai VM j adalah jumlah waktu eksekusi seluruh task yang ditugaskan
 * padanya. MAKESPAN = waktu selesai terbesar di antara semua VM.
 */
public final class SchedulingProblem implements ObjectiveFunction {

    private final double[] taskLength;   // MI
    private final double[] vmMips;       // MIPS
    private final String name;

    public SchedulingProblem(double[] taskLength, double[] vmMips) {
        if (taskLength.length == 0) throw new IllegalArgumentException("task kosong");
        if (vmMips.length == 0) throw new IllegalArgumentException("VM kosong");
        this.taskLength = taskLength.clone();
        this.vmMips = vmMips.clone();
        this.name = "Scheduling(" + taskLength.length + " task, " + vmMips.length + " VM)";
    }

    public int taskCount() { return taskLength.length; }
    public int vmCount()   { return vmMips.length; }
    public double[] taskLengths() { return taskLength.clone(); }
    public double[] vmMipsArray() { return vmMips.clone(); }

    /** Terjemahkan vektor kontinu menjadi penugasan task -> VM. */
    public int[] decode(double[] x) {
        final int m = vmMips.length;
        int[] assign = new int[x.length];
        for (int i = 0; i < x.length; i++) {
            int v = (int) Math.floor(x[i]);
            if (v < 0) v = 0;
            if (v >= m) v = m - 1;      // tangani tepi x_i == M
            assign[i] = v;
        }
        return assign;
    }

    /** Waktu selesai tiap VM untuk sebuah penugasan. */
    public double[] completionTimes(int[] assign) {
        double[] finish = new double[vmMips.length];
        for (int i = 0; i < assign.length; i++)
            finish[assign[i]] += taskLength[i] / vmMips[assign[i]];
        return finish;
    }

    public double makespan(int[] assign) {
        double mk = 0;
        for (double t : completionTimes(assign)) mk = Math.max(mk, t);
        return mk;
    }

    /** Fungsi objektif: MINIMASI MAKESPAN. */
    @Override public double evaluate(double[] x) { return makespan(decode(x)); }

    @Override public int dimension() { return taskLength.length; }

    @Override public double[] lowerBound() {
        double[] lb = new double[taskLength.length];           // semua 0
        return lb;
    }

    @Override public double[] upperBound() {
        double[] ub = new double[taskLength.length];
        // Batas atas eksklusif secara efektif: floor(x) < M dijamin oleh decode().
        java.util.Arrays.fill(ub, vmMips.length - 1e-9);
        return ub;
    }

    @Override public String name() { return name; }

    /**
     * Batas bawah teoretis makespan: total beban dibagi total kapasitas.
     * Tidak selalu tercapai (task tak bisa dipecah), tetapi berguna sebagai
     * acuan seberapa jauh sebuah jadwal dari ideal.
     */
    public double lowerBoundMakespan() {
        double totalWork = 0, totalCap = 0;
        for (double l : taskLength) totalWork += l;
        for (double c : vmMips) totalCap += c;
        return totalWork / totalCap;
    }
}
