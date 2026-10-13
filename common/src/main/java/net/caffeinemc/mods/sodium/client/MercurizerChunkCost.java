package net.caffeinemc.mods.sodium.client;

public final class MercurizerChunkCost {
    private static final double SMOOTHING = 0.05;

    private static double nanosPerEffort = -1;

    private MercurizerChunkCost() {}

    public static synchronized void record(int effort, long elapsedNs) {
        if (effort <= 0 || elapsedNs <= 0) return;
        double sample = (double) elapsedNs / effort;
        if (nanosPerEffort < 0) {
            nanosPerEffort = sample;
        } else {
            nanosPerEffort += (sample - nanosPerEffort) * SMOOTHING;
        }
    }

    public static synchronized double getNanosPerEffort() {
        return nanosPerEffort;
    }
}
