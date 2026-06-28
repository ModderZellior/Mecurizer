package net.caffeinemc.mods.sodium.client;

public final class MercurizerFrameTracker {
    private static final int    WINDOW             = 16;
    private static final long   STABLE_DURATION_NS = 30_000_000_000L;
    private static final double STABLE_VARIANCE     = 0.50;

    private static final long[] frames  = new long[WINDOW];
    private static int  index           = 0;
    private static int  count           = 0;
    private static long bestFrameNs     = Long.MAX_VALUE;

    private static long   stableStartNs      = -1;
    private static boolean refinedThisSession = false;
    private static double  stableFractionSum  = 0;
    private static double  stableBudgetSum    = 0;
    private static long    stableSamples      = 0;

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

        if (!refinedThisSession && count == WINDOW) {
            if (isStable()) {
                if (stableStartNs < 0) stableStartNs = System.nanoTime();
            } else {
                stableStartNs = -1;
                stableFractionSum = 0;
                stableBudgetSum = 0;
                stableSamples = 0;
            }
        }
    }

    public static void accumulateDynamicValues(float fraction, long budget) {
        if (!refinedThisSession && stableStartNs >= 0) {
            stableFractionSum += fraction;
            stableBudgetSum += budget;
            stableSamples++;
        }
    }

    public static boolean isRefinementReady() {
        return !refinedThisSession
                && stableStartNs >= 0
                && stableSamples > 0
                && (System.nanoTime() - stableStartNs) >= STABLE_DURATION_NS;
    }

    public static float getRefinedFraction() {
        return stableSamples > 0 ? (float) (stableFractionSum / stableSamples) : 0;
    }

    public static long getRefinedBudgetNs() {
        return stableSamples > 0 ? (long) (stableBudgetSum / stableSamples) : 0;
    }

    public static void markRefined() {
        refinedThisSession = true;
    }

    private static boolean isStable() {
        long avg = smoothed();
        for (int i = 0; i < count; i++) {
            if (Math.abs(frames[i] - avg) > avg * STABLE_VARIANCE) return false;
        }
        return true;
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

    private static float lastDynamicFraction = 0;

    public static float getDynamicUploadFraction(float base) {
        long avg = smoothed();
        long tgt = target();
        float result;
        if (avg <= tgt) {
            result = base;
        } else {
            double pressure = (double) avg / tgt;
            result = (float) (base * Math.max(0.3, 1.0 - (pressure - 1.0) * 0.5));
        }
        lastDynamicFraction = result;
        return result;
    }

    public static long getDynamicMinBudgetNs(long base) {
        long avg = smoothed();
        long tgt = target();
        long result;
        if (avg <= tgt) {
            result = base;
        } else {
            double pressure = (double) avg / tgt;
            result = (long) (base * Math.max(0.25, 1.0 - (pressure - 1.0) * 0.6));
        }
        accumulateDynamicValues(lastDynamicFraction, result);
        return result;
    }
}
