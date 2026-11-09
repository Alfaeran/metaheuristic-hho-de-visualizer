package id.kelompok2.hho.scheduling;

/**
 * Heuristik penjadwalan klasik sebagai pembanding "sekali jalan".
 * Semuanya deterministik: tidak ada seed, hasilnya tetap untuk data yang sama.
 */
public final class Heuristics {

    private Heuristics() {}

    /** FCFS / Round-Robin: task ke-i ditugaskan ke VM (i mod M). */
    public static int[] roundRobin(SchedulingProblem p) {
        int[] a = new int[p.taskCount()];
        for (int i = 0; i < a.length; i++) a[i] = i % p.vmCount();
        return a;
    }

    /**
     * FCFS versi cloud: task diproses sesuai urutan kedatangan, masing-masing
     * ditempatkan pada VM yang paling cepat tersedia (earliest finish time).
     */
    public static int[] fcfs(SchedulingProblem p) {
        double[] len = p.taskLengths(), mips = p.vmMipsArray();
        double[] free = new double[mips.length];
        int[] a = new int[len.length];
        for (int i = 0; i < len.length; i++) {
            int best = earliestFinish(free, len[i], mips);
            a[i] = best;
            free[best] += len[i] / mips[best];
        }
        return a;
    }

    /** SJF: task terpendek didahulukan, lalu ke VM yang paling cepat selesai. */
    public static int[] sjf(SchedulingProblem p) {
        double[] len = p.taskLengths(), mips = p.vmMipsArray();
        Integer[] order = sortedIndices(len, true);
        double[] free = new double[mips.length];
        int[] a = new int[len.length];
        for (int idx : order) {
            int best = earliestFinish(free, len[idx], mips);
            a[idx] = best;
            free[best] += len[idx] / mips[best];
        }
        return a;
    }

    /**
     * Min-Min: berulang kali pilih task dengan waktu penyelesaian MINIMUM
     * terkecil, lalu jadwalkan. Baik untuk makespan, cenderung menelantarkan
     * task panjang.
     */
    public static int[] minMin(SchedulingProblem p) { return minMaxCore(p, true); }

    /**
     * Max-Min: pilih task dengan waktu penyelesaian minimum TERBESAR lebih
     * dulu. Menyeimbangkan beban lebih baik saat panjang task sangat beragam.
     */
    public static int[] maxMin(SchedulingProblem p) { return minMaxCore(p, false); }

    private static int[] minMaxCore(SchedulingProblem p, boolean pickMin) {
        double[] len = p.taskLengths(), mips = p.vmMipsArray();
        int n = len.length, m = mips.length;
        double[] free = new double[m];
        boolean[] done = new boolean[n];
        int[] a = new int[n];

        for (int k = 0; k < n; k++) {
            int chosenTask = -1, chosenVm = -1;
            double chosenCt = pickMin ? Double.MAX_VALUE : -1;
            for (int i = 0; i < n; i++) {
                if (done[i]) continue;
                // waktu penyelesaian minimum task i di seluruh VM
                int bestVm = 0; double bestCt = Double.MAX_VALUE;
                for (int j = 0; j < m; j++) {
                    double ct = free[j] + len[i] / mips[j];
                    if (ct < bestCt) { bestCt = ct; bestVm = j; }
                }
                boolean better = pickMin ? (bestCt < chosenCt) : (bestCt > chosenCt);
                if (better) { chosenCt = bestCt; chosenTask = i; chosenVm = bestVm; }
            }
            done[chosenTask] = true;
            a[chosenTask] = chosenVm;
            free[chosenVm] += len[chosenTask] / mips[chosenVm];
        }
        return a;
    }

    private static int earliestFinish(double[] free, double length, double[] mips) {
        int best = 0; double bestCt = Double.MAX_VALUE;
        for (int j = 0; j < mips.length; j++) {
            double ct = free[j] + length / mips[j];
            if (ct < bestCt) { bestCt = ct; best = j; }
        }
        return best;
    }

    private static Integer[] sortedIndices(double[] v, boolean ascending) {
        Integer[] idx = new Integer[v.length];
        for (int i = 0; i < v.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (x, y) ->
            ascending ? Double.compare(v[x], v[y]) : Double.compare(v[y], v[x]));
        return idx;
    }
}
