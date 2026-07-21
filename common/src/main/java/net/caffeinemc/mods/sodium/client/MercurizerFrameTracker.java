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

    private static float currentFraction    = -1;
    private static long  currentBudget      = -1;
    private static volatile long recoveryUntilNs = 0;

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

        if (count == WINDOW && frameNs > smoothed() * 2) {
            recoveryUntilNs = System.nanoTime() + 2_000_000_000L;
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

    public static void resetRefinementWindow() {
        stableStartNs    = -1;
        stableFractionSum = 0;
        stableBudgetSum   = 0;
        stableSamples     = 0;
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

    public static float getDynamicUploadFraction(float base) {
        if (currentFraction < 0) currentFraction = base;
        long avg = smoothed();
        long tgt = target();
        float ideal;
        if (avg <= tgt) {
            ideal = base;
        } else {
            double pressure = (double) avg / tgt;
            ideal = (float) (base * Math.max(0.3, 1.0 - (pressure - 1.0) * 0.5));
        }
        if (ideal < currentFraction) {
            currentFraction += (ideal - currentFraction) * 0.3f;
        } else {
            currentFraction += (ideal - currentFraction) * 0.02f;
        }
        if (System.nanoTime() < recoveryUntilNs) return Math.min(currentFraction, base * 0.50f);
        return currentFraction;
    }

    public static long getDynamicMinBudgetNs(long base) {
        if (currentBudget < 0) currentBudget = base;
        long avg = smoothed();
        long tgt = target();
        long ideal;
        if (avg <= tgt) {
            ideal = base;
        } else {
            double pressure = (double) avg / tgt;
            ideal = (long) (base * Math.max(0.25, 1.0 - (pressure - 1.0) * 0.6));
        }
        if (ideal < currentBudget) {
            currentBudget += (long) ((ideal - currentBudget) * 0.3);
        } else {
            currentBudget += (long) ((ideal - currentBudget) * 0.02);
        }
        accumulateDynamicValues(currentFraction, currentBudget);
        return currentBudget;
    }
}
