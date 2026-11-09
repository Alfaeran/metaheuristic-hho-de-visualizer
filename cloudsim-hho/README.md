# Pengujian Harris Hawks Optimization pada CloudSim

Pengujian algoritma **Harris Hawks Optimization (HHO)** untuk penjadwalan task
di lingkungan cloud, disimulasikan dengan **CloudSim**. Mencakup empat lapis:
test case korektheid, benchmark fungsi standar, benchmark penjadwalan, dan
performance test.

Referensi utama:

- **[1]** A. A. Heidari, S. Mirjalili, H. Faris, I. Aljarah, M. Mafarja, H. Chen,
  "Harris hawks optimization: Algorithm and applications," *Future Generation
  Computer Systems*, vol. 97, pp. 849–872, 2019. doi:10.1016/j.future.2019.02.028
- **[2]** Kode MATLAB resmi penulis:
  github.com/aliasgharheidaricom/Harris-Hawks-Optimization-Algorithm-and-Applications
- **[3]** "Enhanced Harris Hawks Optimization Algorithm for Task Scheduling,"
  *IJACSA*, vol. 15, no. 7, 2024 — sumber konfigurasi CloudSim.
- **[4]** R. N. Calheiros dkk., "CloudSim: a toolkit for modeling and simulation
  of cloud computing environments," *Software: Practice and Experience*, 2011.

---

## Cara menjalankan

```bash
cd cloudsim-hho

# Test case (20 pengujian korektheid)
mvn test

# Benchmark fungsi F1-F23  (argumen: jumlah run, jumlah iterasi)
mvn exec:java -Dexec.mainClass=id.kelompok2.hho.experiment.BenchmarkExperiment -Dexec.args="30 500"

# Benchmark penjadwalan di CloudSim
mvn exec:java -Dexec.mainClass=id.kelompok2.hho.experiment.CloudSimExperiment -Dexec.args="10 200"

# Anggaran iterasi vs mutu solusi
mvn exec:java -Dexec.mainClass=id.kelompok2.hho.experiment.ConvergenceExperiment

# Diagnostik lanskap pencarian
mvn exec:java -Dexec.mainClass=id.kelompok2.hho.experiment.LandscapeDiagnostics

# Performance test
mvn exec:java -Dexec.mainClass=id.kelompok2.hho.experiment.PerformanceTest
```

Butuh JDK 17+ dan Maven. Seluruh hasil ditulis ke `results/`.

---

## Catatan pemilihan pustaka CloudSim

Paket **CloudSim klasik** (`org.cloudbus.cloudsim:cloudsim:4.0`) **tidak tersedia
di Maven Central** — diverifikasi mengembalikan HTTP 404. Membangun proyek dengan
pustaka itu menuntut JAR yang dipasang manual, sehingga hasilnya tidak dapat
direproduksi oleh penguji lain.

Proyek ini memakai **CloudSim Plus 8.5.7**, fork resmi CloudSim yang terbit di
Maven Central dengan model simulasi yang sama (datacenter, host, VM, cloudlet,
broker, scheduler space-shared/time-shared). Bila pengujian mensyaratkan paket
`org.cloudbus.cloudsim` persis, hanya kelas `sim/CloudSimScenario.java` yang
perlu disesuaikan; seluruh algoritma tidak bergantung pada pustaka simulasi.

---

## Parameter pengujian

Diambil dari sumber, bukan dikarang:

| Parameter | Nilai | Sumber |
|---|---|---|
| Populasi (N) | 30 | [1] bagian 4, dan `N=30` pada [2] |
| Iterasi maksimum (T) | 500 | [1] bagian 4, dan `T=500` pada [2] |
| Run independen | 30 | [1] bagian 4 |
| Fungsi benchmark | F1–F23, dim 30 untuk F1–F13 | [1] Tabel 1–3 |
| Uji signifikansi | Wilcoxon rank-sum, α = 0,05 | [1] bagian 4 |
| β Lévy flight | 1,5 | [1] eq.(9) |
| Datacenter | 5 | [3] Tabel I |
| VM | 20, RAM 1024 MB, BW 5 Mbps | [3] Tabel I |
| Host | RAM 16 GB, storage 5 TB | [3] Tabel I |
| Jumlah task | 100–1000 | [3] Tabel I |
| Panjang task | 780.000 MI (±50% ragam) | [3] Tabel I |
| Pembanding | PSO, GA, DE, FCFS, SJF, RR, Min-Min, Max-Min | [1], [3] |

---

## Lapis A — Test case korektheid

`mvn test` menjalankan **20 pengujian**. Setiap pengujian menguji satu invarian
yang dapat diturunkan dari paper, sehingga kegagalannya menunjuk persamaan
tertentu, bukan sekadar "hasil jelek".

| Pengujian | Invarian yang dibuktikan |
|---|---|
| eq.3 selubung energi | `\|E\| ≤ 2(1−t/T)` di seluruh iterasi |
| eq.3 pada t/T=0,9 | selubung tepat 0,2 → tidak ada hawk yang masih menjelajah |
| eq.3 pemetaan fase | EXPLORE hanya saat `\|E\|≥1`; besiege lunak `\|E\|≥0,5`; keras `\|E\|<0,5` |
| eq.9 σ Mantegna | σ = 0,69657454 untuk β = 1,5 |
| eq.9 fungsi Gamma | Γ(n) = (n−1)!, Γ(½) = √π |
| eq.9 selisih varian | varian paper tepat 100× lebih kecil dari varian kode resmi |
| Domain | solusi terbaik selalu di dalam `[lb,ub]` untuk ke-23 fungsi |
| Determinisme | seed sama → identik bit-per-bit; seed beda → populasi awal beda |
| Monotonisitas | kurva konvergensi tidak pernah naik |
| Optimum analitik F1–F13 | nilai fungsi tepat di titik optimum yang diketahui |
| Optimum analitik F14–F23 | idem untuk fungsi berdimensi tetap |
| Konvergensi unimodal | F1 turun di bawah 1e−20 |
| Konvergensi multimodal | F9 Rastrigin mendekati 0 |
| Anggaran evaluasi | jumlah evaluasi berada di `[N·T, 4·N·T]` |
| Decoder random key | 500 percobaan + nilai tepi → jadwal selalu sah |
| Model makespan | makespan = waktu selesai VM terbesar |
| Heuristik sah | FCFS/SJF/RR/Min-Min/Max-Min di atas batas bawah teoretis |
| CloudSim selesai | seluruh cloudlet selesai, metrik dalam rentang wajar |
| Kesepadanan model | makespan CloudSim vs model objektif selisih < 10% |
| Keterjagaan urutan | jadwal lebih baik di model juga lebih baik di CloudSim |

### Dua selisih antara teks paper dan kode resmi penulis

Ditemukan saat menyelaraskan implementasi, dan **diuji, bukan diasumsikan**:

1. **Pemetaan `q` pada eq.(1).** Teks paper menulis `q ≥ 0,5` memakai `X_rand`;
   `HHO.m` resmi memakai `q < 0,5`. Karena `q ~ U(0,1)`, kedua cabang tetap
   terpilih dengan peluang 0,5, sehingga **ekuivalen secara statistik**.
2. **Faktor 0,01 pada Lévy flight.** Teks paper eq.(9) memuat `LF = 0,01·u·σ/|v|^(1/β)`;
   fungsi `Levy()` pada kode resmi **tidak** memuat faktor itu. Keduanya
   **tidak ekuivalen** — ukuran langkahnya berbeda 100×.

Implementasi ini mengikuti **kode resmi** sebagai bawaan, karena angka yang
dilaporkan paper dihasilkan oleh kode itu. Varian paper tersedia lewat
`Config.levyVariant` dan selisih 100×-nya dikunci oleh sebuah test.

---

## Lapis B — Benchmark fungsi F1–F23

Protokol paper: N=30, T=500, **30 run independen**, dim 30 untuk F1–F13.
Pembanding PSO, GA, DE dengan anggaran identik dan deretan seed yang sama.
Uji **Wilcoxon rank-sum**, α = 0,05.

Nilai AVG atas 30 run (`results/benchmark_functions.csv`):

| Fungsi | Optimum | HHO | PSO | GA | DE | HHO |
|---|---|---|---|---|---|---|
| F1 Sphere | 0 | **1,34E−97** | 6,02E−01 | 2,29E+02 | 7,85E+00 | 3M |
| F2 Schwefel 2.22 | 0 | **3,94E−50** | 1,22E−01 | 5,13E+00 | 2,98E−02 | 3M |
| F3 Schwefel 1.2 | 0 | **6,97E−76** | 2,48E+03 | 6,07E+03 | 5,97E+02 | 3M |
| F4 Schwefel 2.21 | 0 | **8,96E−48** | 8,66E+00 | 1,18E+01 | 2,50E+01 | 3M |
| F5 Rosenbrock | 0 | **8,27E−03** | 6,25E+03 | 1,22E+04 | 8,96E+03 | 3M |
| F6 Step | 0 | **1,14E−04** | 5,41E−01 | 2,36E+02 | 2,53E+00 | 3M |
| F7 Quartic+noise | 0 | **1,45E−04** | 5,67E−02 | 9,53E−02 | 1,15E−01 | 3M |
| F8 Schwefel | −12569 | **−1,25E+04** | −8,65E+03 | −9,39E+03 | −7,71E+03 | 3M |
| F9 Rastrigin | 0 | **0,00E+00** | 5,45E+01 | 9,94E+01 | 1,51E+02 | 3M |
| F10 Ackley | 0 | **4,44E−16** | 9,89E−01 | 5,03E+00 | 1,89E+00 | 3M |
| F11 Griewank | 0 | **0,00E+00** | 5,69E−01 | 3,01E+00 | 3,92E−01 | 3M |
| F12 Penalized 1 | 0 | **4,88E−06** | 3,24E−01 | 6,16E+00 | 1,85E+03 | 3M |
| F13 Penalized 2 | 0 | **1,19E−04** | 1,26E+00 | 2,99E+01 | 3,88E+04 | 3M |
| F14 Foxholes | 0,998 | 1,82E+00 | **9,98E−01** | **9,98E−01** | 1,03E+00 | 3K |
| F15 Kowalik | 3,07E−04 | **4,17E−04** | 2,66E−03 | 3,72E−03 | 1,95E−03 | 2M 1S |
| F16 Six-hump | −1,0316 | −1,03E+00 | −1,03E+00 | −1,03E+00 | −1,03E+00 | 3K |
| F17 Branin | 0,3979 | 3,98E−01 | 3,98E−01 | 3,98E−01 | 3,98E−01 | 3K |
| F18 Goldstein | 3 | **3,00E+00** | 3,00E+00 | 5,73E+00 | 3,00E+00 | 1M 2K |
| F19 Hartman 3-D | −3,8628 | −3,86E+00 | −3,86E+00 | −3,86E+00 | −3,86E+00 | 1M 2K |
| F20 Hartman 6-D | −3,3220 | −3,16E+00 | −3,28E+00 | **−3,29E+00** | −3,22E+00 | 3K |
| F21 Shekel m=5 | −10,153 | −5,05E+00 | −5,56E+00 | −6,16E+00 | **−9,73E+00** | 2S 1K |
| F22 Shekel m=7 | −10,403 | −5,09E+00 | −8,78E+00 | −7,20E+00 | **−9,74E+00** | 1S 2K |
| F23 Shekel m=10 | −10,536 | −5,46E+00 | −8,52E+00 | −7,06E+00 | **−1,02E+01** | 1S 2K |

**Total Wilcoxon: HHO menang 43, seri 5, kalah 21 dari 69 perbandingan.**

### Pembacaan hasil

**HHO menang mutlak 39/39 pada F1–F13.** Pada F1 ia mencapai **1,34E−97**,
orde yang sama dengan yang dilaporkan paper [1], sementara PSO masih di 1E−01
dan GA di 1E+02. Pada F9 Rastrigin dan F11 Griewank ia menemukan **optimum
eksak 0**, dan pada F10 Ackley mencapai 4,44E−16 yang merupakan batas presisi
mesin. Ini membuktikan dua hal yang diklaim paper: eksploitasi tajam pada
unimodal (F1–F7) dan kemampuan lolos optimum lokal pada multimodal (F8–F13).

**HHO kalah pada keluarga Shekel (F21–F23)** — hanya mencapai −5,05 dari
optimum −10,15, sementara DE mencapai −9,73. Ini **bukan cacat implementasi**:
kelemahan HHO pada Shekel memang dilaporkan di literatur. Justru reproduksi
kelemahan ini, bersama kekuatan pada F1–F13, menjadi bukti bahwa implementasi
ini berperilaku seperti HHO yang sebenarnya, bukan seperti algoritma lain yang
kebetulan diberi nama HHO.

---

## Lapis C — Benchmark penjadwalan di CloudSim

Setiap jadwal **dijalankan di CloudSim**, dan metrik diambil dari hasil
simulasi, bukan dari rumus. 10 run per metaheuristik, N=30, T=500.
(`results/cloudsim_scheduling.csv`)

Makespan rata-rata (detik), 20 VM, 5 datacenter:

| Algoritma | Jenis | 100 task | 200 task | 500 task | 1000 task |
|---|---|---|---|---|---|
| **Max-Min** | heuristik | **3211,7** | **7085,5** | **15400,1** | **37387,8** |
| FCFS | heuristik | 3531,6 | 7351,0 | 15713,7 | 37708,7 |
| SJF / Min-Min | heuristik | 3544,3 | 7460,4 | 15669,5 | 37806,4 |
| Round-Robin | heuristik | 9189,6 | 18171,1 | 42693,3 | 79270,2 |
| HHO | metaheuristik | 4552,0 | 9779,8 | 27058,1 | 59889,7 |
| PSO | metaheuristik | 4112,8 | 8875,6 | 23152,9 | 68024,8 |
| GA | metaheuristik | 4121,0 | 8654,2 | 22004,2 | 60766,9 |
| DE | metaheuristik | 4879,8 | 9921,9 | 30364,8 | 67521,8 |
| *batas bawah teoretis* | — | *3092,6* | *6881,7* | *15221,9* | *37226,2* |

Utilisasi VM dan degree of imbalance (1000 task):

| Algoritma | Utilisasi | Imbalance | Respons rata-rata (s) |
|---|---|---|---|
| Max-Min | **0,996** | **0,011** | 19132,6 |
| FCFS | 0,983 | 0,036 | 19069,3 |
| HHO | 0,704 | 1,097 | 22754,1 |
| GA | 0,728 | 0,991 | 22358,1 |
| PSO | 0,669 | 1,234 | 23991,5 |

### Temuan yang harus disampaikan apa adanya

**Pada masalah ini, HHO — dan seluruh metaheuristik — KALAH dari heuristik
greedy sederhana.** Max-Min mencapai makespan hanya **3,8% di atas batas bawah
teoretis**, sementara HHO berada 47–78% di atasnya. Ini bertolak belakang
dengan klaim banyak paper HHO-penjadwalan, dan penyebabnya ditelusuri pada
lapis diagnostik di bawah, bukan disembunyikan.

Satu hal yang tetap mendukung klaim paper: **pada dimensi tertinggi (1000
task) HHO mengungguli PSO (59.890 vs 68.025) dan DE (67.522), serta seri
dengan GA.** Keunggulan relatif HHO terhadap metaheuristik lain memang
membesar seiring naiknya dimensi, sesuai klaim [1]. Yang tidak terbukti adalah
keunggulan terhadap heuristik greedy.

---

## Diagnostik — mengapa metaheuristik kalah di sini

`LandscapeDiagnostics` menguji tiga hipotesis (`results/landscape_diagnostics.txt`):

**1. Lanskap pencariannya datar berundak.** Fungsi objektif memakai
`floor(x_i)` untuk memilih VM, sehingga perubahan kecil pada `x` tidak
mengubah makespan sama sekali:

| perturbasi ε | % yang mengubah makespan |
|---|---|
| 0,001 | 0,0% |
| 0,01 | 0,0% |
| 0,1 | 0,2% |
| 0,5 | 2,1% |

Seluruh operator HHO — eq.(1), (4), (6), (10), (11) — bersifat **kontinu**.
Pada lanskap seperti ini mereka kehilangan arah perbaikan: 98% gerakan
menghasilkan jadwal yang identik secara diskret.

**2. Pencarian mencicipi bagian ruang yang sangat kecil.** Dari 32.224
evaluasi, hanya **10.372 jadwal unik** (32,2%) — sisanya duplikat akibat
kedataran di atas. Ruang pencariannya 20¹⁰⁰ ≈ 10¹³⁰.

**3. Max-Min sudah berada di optimum lokal.** Hill-climbing diskret yang
mencoba memindahkan setiap task ke setiap VM lain **tidak menemukan satu pun
perbaikan** (0 langkah dari 100×20 kemungkinan per sapuan). Jadi bukan HHO
yang lemah — solusi Max-Min memang sudah tidak dapat diperbaiki oleh
perpindahan satu task, dan hanya 3,8% dari batas bawah teoretis.

### Pengaruh anggaran iterasi

`ConvergenceExperiment`, 100 task (`results/convergence_budget.csv`):

| T | HHO | HHO+seed | PSO | GA |
|---|---|---|---|---|
| 50 | 4896,4 | 3211,0 | 5032,6 | 4460,0 |
| 500 | 4414,4 | 3211,0 | 4173,7 | 4189,4 |
| 2000 | 4186,0 | 3211,0 | 3920,4 | 4005,7 |
| 5000 | 4255,9 | 3211,0 | 3678,2 | 3770,3 |
| *Max-Min* | — | *3211,0* | — | — |

Menaikkan anggaran 100× hanya memperbaiki HHO dari 4896 ke 4256 — **stagnan,
tidak pernah menyentuh 3211**. Varian **HHO+seed**, yang menyisipkan solusi
heuristik ke populasi awal tanpa mengubah satu pun persamaan, langsung
mencapai 3211 dan **tidak pernah berhasil memperbaikinya** — konsisten dengan
temuan bahwa Max-Min sudah optimum lokal.

Kesimpulannya sesuai **teorema No Free Lunch**: keunggulan HHO yang terbukti
telak pada lanskap kontinu F1–F13 tidak berpindah ke lanskap diskret berundak.
Encoding random key, meski lazim dipakai di literatur, adalah pilihan yang
buruk untuk masalah ini.

---

## Lapis D — Performance test

`results/performance.csv`. Rata-rata 5 pengulangan setelah 3 pemanasan JIT.

**Skalabilitas terhadap dimensi** (Sphere, N=30, T=500) — **linier**:

| dim | waktu (ms) | ms per 1000 evaluasi |
|---|---|---|
| 10 | 12,6 | 0,43 |
| 30 | 19,3 | 0,66 |
| 50 | 28,2 | 0,97 |
| 100 | 44,2 | 1,51 |
| 200 | 89,0 | 3,05 |
| 500 | 184,9 | 6,35 |

Dimensi naik 50× → waktu naik 14,7×. Biaya per evaluasi tumbuh linier terhadap
dimensi, sesuai struktur algoritma yang memproses `dim` elemen per hawk.

**Skalabilitas terhadap populasi dan iterasi** (dim 30):

| N | T | waktu (ms) |
|---|---|---|
| 10 | 500 | 5,9 |
| 30 | 500 | 14,8 |
| 50 | 500 | 19,1 |
| 100 | 500 | 59,4 |
| 30 | 1000 | 30,8 |
| 30 | 2000 | 88,9 |

**Biaya per algoritma** (dim 30, N=30, T=500):

| Algoritma | waktu (ms) | evaluasi fungsi |
|---|---|---|
| HHO | 14,8 | 29.216 |
| PSO | 28,9 | 15.030 |
| GA | 39,3 | 14.530 |
| DE | 14,8 | 15.030 |

HHO memakai **~2× lebih banyak evaluasi** pada anggaran iterasi yang sama,
karena fase rapid dive eq.(10)/(11) mengevaluasi kandidat `Y` dan `Z`
tambahan. Meski begitu waktu dindingnya tetap terendah bersama DE, sebab
operatornya murni aritmetika vektor tanpa pengurutan.

**Biaya simulasi CloudSim** (20 VM, 5 datacenter):

| task | waktu simulasi (ms) |
|---|---|
| 100 | 52,8 |
| 200 | 56,4 |
| 500 | 98,8 |
| 1000 | 212,6 |
| 2000 | 708,0 |

**Jejak memori**: 3,0 MB pada dim 30; 9,2 MB pada dim 1000.

---

## Kesimpulan

**Apakah HHO bekerja sesuai teori dan jurnalnya? Ya, terbukti pada ranahnya.**

1. **Sesuai teori** — 20 test case mengunci invarian persamaan (1)–(11):
   selubung energi `|E| ≤ 2(1−t/T)`, pemetaan fase terhadap `|E|`, σ Mantegna
   0,69657454, penanganan domain, determinisme, dan monotonisitas.

2. **Sesuai jurnal** — pada protokol persis paper (N=30, T=500, 30 run),
   HHO **menang 39/39** pada F1–F13 dengan nilai yang ordenya sama seperti
   dilaporkan paper (F1: 1,34E−97), dan **mereproduksi kelemahannya** pada
   keluarga Shekel F21–F23.

3. **Pada penjadwalan CloudSim, HHO kalah dari heuristik greedy.** Ini
   dilaporkan apa adanya, lengkap dengan diagnostik yang menunjukkan
   penyebabnya: lanskap random key datar berundak (98% gerakan tidak mengubah
   solusi) dan Max-Min sudah berada di optimum lokal 3,8% dari batas bawah.

### Keterbatasan

- Encoding **random key** adalah titik lemah utama. Encoding berbasis
  permutasi + list scheduling kemungkinan besar jauh lebih efektif, tetapi
  tidak diuji di sini.
- Perbandingan memakai **satu profil beban** (780.000 MI ±50%, VM 4 tingkat
  MIPS). Jejak nyata seperti HPC2N atau NASA belum diuji.
- Metrik **biaya dan energi** belum dihitung; baru makespan, waktu respons,
  utilisasi, imbalance, dan throughput.
- Simulasi memakai **CloudSim Plus**, bukan `org.cloudbus.cloudsim` klasik
  (alasan di bagian atas).

## Struktur kode

```
src/main/java/id/kelompok2/hho/
├── core/         HHO, Lévy flight, PRNG ber-seed, antarmuka optimizer
├── benchmark/    F1–F23, baseline PSO/GA/DE
├── scheduling/   encoding random key, heuristik, varian HHO+seed
├── sim/          skenario dan runner CloudSim
└── experiment/   empat lapis eksperimen + uji statistik
src/test/java/    20 test case korektheid
results/          seluruh keluaran CSV dan log
```
