package net.caffeinemc.mods.sodium.client;

public final class MercurizerFrameTracker {
    private static final int WINDOW = 8;
    private static final long TARGET_NS = 16_666_667L;

    private static final long[] frames = new long[WINDOW];
    private static int index = 0;
    private static int count = 0;

    private MercurizerFrameTracker() {}

    public static void record(long frameNs) {
        if (frameNs <= 0) return;
        frames[index] = frameNs;
        index = (index + 1) % WINDOW;
        if (count < WINDOW) count++;
    }

    private static long smoothed() {
        if (count == 0) return TARGET_NS;
        long sum = 0;
        for (int i = 0; i < count; i++) sum += frames[i];
        return sum / count;
    }

    public static float getDynamicUploadFraction(float base) {
        long avg = smoothed();
        if (avg <= TARGET_NS) return base;
        double pressure = (double) avg / TARGET_NS;
        double scale = Math.max(0.3, 1.0 - (pressure - 1.0) * 0.5);
        return (float) (base * scale);
    }

    public static long getDynamicMinBudgetNs(long base) {
        long avg = smoothed();
        if (avg <= TARGET_NS) return base;
        double pressure = (double) avg / TARGET_NS;
        double scale = Math.max(0.25, 1.0 - (pressure - 1.0) * 0.6);
        return (long) (base * scale);
    }
}
