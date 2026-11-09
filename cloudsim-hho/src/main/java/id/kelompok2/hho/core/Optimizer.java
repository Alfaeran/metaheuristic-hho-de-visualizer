package id.kelompok2.hho.core;

/** Kontrak bersama agar HHO dan algoritma pembanding dapat diadu setara. */
public interface Optimizer {
    String name();
    OptimizationResult optimize(ObjectiveFunction f);
}
