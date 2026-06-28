package net.caffeinemc.mods.sodium.client;

public final class MercurizerFrameTracker {
    private static final int WINDOW = 16;

    private static final long[] frames = new long[WINDOW];
    private static int index = 0;
    private static int count = 0;
    private static long bestFrameNs = Long.MAX_VALUE;

    private MercurizerFrameTracker() {}

    public static void record(long frameNs) {
        if (frameNs <= 0) return;
        frames[index] = frameNs;
        index = (index + 1) % WINDOW;
        if (count < WINDOW) count++;

        if (frameNs < bestFrameNs) {
            bestFrameNs = frameNs;
        } else {
            bestFrameNs = (long) (bestFrameNs * 0.9999 + frameNs * 0.0001);
        }
    }

    private static long smoothed() {
        if (count == 0) return bestFrameNs == Long.MAX_VALUE ? 16_666_667L : bestFrameNs;
        long sum = 0;
        for (int i = 0; i < count; i++) sum += frames[i];
        return sum / count;
    }

    private static long target() {
        return bestFrameNs == Long.MAX_VALUE ? 16_666_667L : bestFrameNs;
    }

    public static float getDynamicUploadFraction(float base) {
        long avg = smoothed();
        long tgt = target();
        if (avg <= tgt) return base;
        double pressure = (double) avg / tgt;
        double scale = Math.max(0.3, 1.0 - (pressure - 1.0) * 0.5);
        return (float) (base * scale);
    }

    public static long getDynamicMinBudgetNs(long base) {
        long avg = smoothed();
        long tgt = target();
        if (avg <= tgt) return base;
        double pressure = (double) avg / tgt;
        double scale = Math.max(0.25, 1.0 - (pressure - 1.0) * 0.6);
        return (long) (base * scale);
    }
}
