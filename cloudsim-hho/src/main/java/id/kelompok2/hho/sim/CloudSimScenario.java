package id.kelompok2.hho.sim;

import id.kelompok2.hho.core.Rng;
import id.kelompok2.hho.scheduling.SchedulingProblem;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;

/**
 * Skenario simulasi CloudSim.
 *
 * Konfigurasi mengikuti paper HHO untuk penjadwalan cloud
 * (Enhanced Harris Hawks Optimization, IJACSA 15(7) 2024, Tabel I):
 *   datacenter 5, VM 20, RAM VM 1024 MB, bandwidth VM 5 Mbps,
 *   RAM host 16 GB, storage host 5 TB, bandwidth jaringan 1000 Mbps,
 *   jumlah task 100-1000, panjang task 780.000 MI.
 *
 * Heterogenitas MIPS VM ditambahkan karena lingkungan cloud nyata memang
 * heterogen; tanpa itu seluruh algoritma penjadwalan akan menghasilkan
 * makespan yang nyaris sama dan perbandingannya kehilangan makna.
 */
public final class CloudSimScenario {

    // ---- parameter sesuai tabel paper
    public static final int    DATACENTERS   = 5;
    public static final int    HOSTS_PER_DC  = 4;
    public static final int    HOST_PES      = 8;
    public static final double HOST_MIPS     = 2000;
    public static final long   HOST_RAM      = 16_384;      // 16 GB
    public static final long   HOST_STORAGE  = 5_000_000;   // 5 TB
    public static final long   HOST_BW       = 1000;        // 1000 Mbps

    public static final int    VM_COUNT      = 20;
    public static final int    VM_PES        = 1;
    public static final long   VM_RAM        = 1024;        // 1024 MB
    public static final long   VM_BW         = 5;           // 5 Mbps
    public static final long   VM_SIZE       = 10_000;

    public static final long   TASK_LENGTH   = 780_000;     // MI
    public static final int    TASK_PES      = 1;

    /** Kapasitas MIPS tiap VM, heterogen tetapi deterministik. */
    public static double[] vmMipsProfile(int vmCount, long seed) {
        Rng rng = new Rng(seed);
        double[] mips = new double[vmCount];
        // Tiga kelas VM seperti penawaran cloud nyata (kecil/sedang/besar)
        final double[] tiers = {500, 1000, 1500, 2000};
        for (int i = 0; i < vmCount; i++) mips[i] = tiers[rng.nextInt(tiers.length)];
        return mips;
    }

    /**
     * Panjang tiap task. Panjang dasar 780.000 MI sesuai tabel paper, diberi
     * ragam +/-50% agar penjadwalan menjadi masalah yang tidak trivial.
     */
    public static double[] taskLengthProfile(int taskCount, long seed) {
        Rng rng = new Rng(seed);
        double[] len = new double[taskCount];
        for (int i = 0; i < taskCount; i++)
            len[i] = TASK_LENGTH * (0.5 + rng.nextDouble());
        return len;
    }

    /** Masalah penjadwalan yang setara dengan skenario CloudSim di bawah. */
    public static SchedulingProblem problem(int taskCount, int vmCount, long seed) {
        return new SchedulingProblem(
            taskLengthProfile(taskCount, seed),
            vmMipsProfile(vmCount, seed));
    }

    /** Hasil satu kali menjalankan simulasi CloudSim. */
    public static final class SimOutcome {
        public double makespan;              // detik
        public double avgResponseTime;       // detik
        public double avgWaitingTime;        // detik
        public double throughput;            // task per detik
        public double avgVmUtilization;      // 0..1
        public double degreeOfImbalance;     // (maks-min)/rata-rata
        public int    finishedCloudlets;
        public double[] vmFinishTime;
    }

    /**
     * Jalankan simulasi CloudSim nyata dengan penugasan task->VM yang sudah
     * ditentukan algoritma penjadwalan.
     *
     * @param assignment assignment[i] = indeks VM untuk task ke-i
     */
    public static SimOutcome run(double[] taskLengths, double[] vmMips, int[] assignment) {
        // CloudSim Plus menulis log cukup ramai; matikan agar keluaran
        // eksperimen tetap terbaca.
        org.cloudsimplus.util.Log.setLevel(ch.qos.logback.classic.Level.OFF);

        final CloudSimPlus sim = new CloudSimPlus();

        // ---- Datacenter dan host
        List<Datacenter> datacenters = new ArrayList<>();
        for (int d = 0; d < DATACENTERS; d++) {
            List<Host> hosts = new ArrayList<>();
            for (int h = 0; h < HOSTS_PER_DC; h++) {
                List<Pe> pes = new ArrayList<>();
                for (int p = 0; p < HOST_PES; p++) pes.add(new PeSimple(HOST_MIPS));
                Host host = new HostSimple(HOST_RAM, HOST_BW, HOST_STORAGE, pes);
                host.setVmScheduler(new VmSchedulerTimeShared());
                hosts.add(host);
            }
            datacenters.add(new DatacenterSimple(sim, hosts));
        }

        final DatacenterBroker broker = new DatacenterBrokerSimple(sim);

        // ---- VM dengan kapasitas MIPS heterogen
        List<Vm> vms = new ArrayList<>();
        for (int i = 0; i < vmMips.length; i++) {
            Vm vm = new VmSimple(i, vmMips[i], VM_PES);
            vm.setRam(VM_RAM).setBw(VM_BW).setSize(VM_SIZE);
            // Space-shared: task pada satu VM dieksekusi berurutan, sesuai
            // model waktu yang dipakai fungsi objektif.
            vm.setCloudletScheduler(new CloudletSchedulerSpaceShared());
            vms.add(vm);
        }

        // ---- Cloudlet (task)
        UtilizationModelFull full = new UtilizationModelFull();
        List<Cloudlet> cloudlets = new ArrayList<>();
        for (int i = 0; i < taskLengths.length; i++) {
            Cloudlet c = new CloudletSimple(i, (long) taskLengths[i], TASK_PES);
            c.setFileSize(300).setOutputSize(300).setUtilizationModel(full);
            cloudlets.add(c);
        }

        broker.submitVmList(vms);
        broker.submitCloudletList(cloudlets);

        // ---- Terapkan keputusan algoritma penjadwalan
        for (int i = 0; i < cloudlets.size(); i++) {
            broker.bindCloudletToVm(cloudlets.get(i), vms.get(assignment[i]));
        }

        sim.start();

        // ---- Kumpulkan metrik dari hasil simulasi, bukan dari model matematis
        List<Cloudlet> finished = broker.getCloudletFinishedList();
        SimOutcome o = new SimOutcome();
        o.finishedCloudlets = finished.size();

        double makespan = 0, sumResponse = 0, sumWait = 0;
        for (Cloudlet c : finished) {
            makespan     = Math.max(makespan, c.getFinishTime());
            sumResponse += c.getFinishTime() - c.getSubmissionDelay();
            sumWait     += c.getStartTime()  - c.getSubmissionDelay();
        }
        o.makespan        = makespan;
        o.avgResponseTime = finished.isEmpty() ? 0 : sumResponse / finished.size();
        o.avgWaitingTime  = finished.isEmpty() ? 0 : sumWait / finished.size();
        o.throughput      = makespan > 0 ? finished.size() / makespan : 0;

        // Waktu sibuk tiap VM dihitung dari cloudlet yang dijalankannya.
        double[] busy = new double[vms.size()];
        for (Cloudlet c : finished) {
            int idx = (int) c.getVm().getId();
            if (idx >= 0 && idx < busy.length) busy[idx] += c.getTotalExecutionTime();
        }
        o.vmFinishTime = busy;

        double sumUtil = 0, maxB = 0, minB = Double.MAX_VALUE;
        for (double b : busy) {
            sumUtil += (makespan > 0 ? b / makespan : 0);
            maxB = Math.max(maxB, b);
            minB = Math.min(minB, b);
        }
        o.avgVmUtilization = sumUtil / vms.size();

        // Degree of Imbalance = (maks - min) / rata-rata waktu sibuk VM.
        // Semakin kecil semakin merata beban antar-VM.
        double avgB = 0;
        for (double b : busy) avgB += b;
        avgB /= busy.length;
        o.degreeOfImbalance = avgB > 0 ? (maxB - minB) / avgB : 0;

        return o;
    }

    private CloudSimScenario() {}
}
