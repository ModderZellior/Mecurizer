package net.caffeinemc.mods.sodium.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MercurizerFrameTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mercurizer");

    private static volatile long   uploadBudgetNs   = 2_000_000L;
    private static volatile float  uploadFraction   = 0.25f;
    private static volatile long   minUploadBudgetNs = 500_000L;

    private static final int   WINDOW_SIZE   = 60;
    private static final float SCALE_UP      = 1.08f;
    private static final float SCALE_DOWN    = 0.88f;
    private static final float TARGET_MARGIN = 0.90f;

    private static final long[] frameTimes    = new long[WINDOW_SIZE];
    private static int          frameIndex    = 0;
    private static int          frameCount    = 0;
    private static long         lastFrameNs   = -1;

    // Refinement window state
    static volatile long  stableStartNs     = -1;
    static volatile float stableFractionSum = 0;
    static volatile float stableBudgetSum   = 0;
    static volatile int   stableSamples     = 0;

    public static void onFrameStart() {
        long now = System.nanoTime();
        if (lastFrameNs > 0) {
            long dt = now - lastFrameNs;
            frameTimes[frameIndex % WINDOW_SIZE] = dt;
            frameIndex++;
            if (frameCount < WINDOW_SIZE) frameCount++;
        }
        lastFrameNs = now;
    }

    public static long getUploadBudgetNs() { return uploadBudgetNs; }
    public static float getUploadFraction() { return uploadFraction; }

    public static void onUploadComplete(long uploadNs, long targetFrameNs) {
        long budget = uploadBudgetNs;
        if (uploadNs > targetFrameNs * TARGET_MARGIN) {
            budget = Math.max(minUploadBudgetNs, (long) (budget * SCALE_DOWN));
        } else if (uploadNs < targetFrameNs * TARGET_MARGIN * 0.5f) {
            budget = (long) (budget * SCALE_UP);
        }
        uploadBudgetNs = budget;
    }

    public static long getP95FrameTimeNs() {
        if (frameCount == 0) return 16_666_666L;
        int n = frameCount;
        long[] copy = new long[n];
        for (int i = 0; i < n; i++) copy[i] = frameTimes[i];
        java.util.Arrays.sort(copy);
        return copy[(int) (n * 0.95)];
    }

    public static double getRecentMeanFrameTimeNs() {
        if (frameCount == 0) return 16_666_666.0;
        int n = frameCount;
        double sum = 0;
        for (int i = 0; i < n; i++) sum += frameTimes[i];
        return sum / n;
    }

    public static void configure(long budgetNs, float fraction, long minBudgetNs) {
        uploadBudgetNs   = budgetNs;
        uploadFraction   = fraction;
        minUploadBudgetNs = minBudgetNs;
        LOGGER.info("[Mercurizer] Upload budget set: {}us, fraction: {}, min: {}us",
                budgetNs / 1000, fraction, minBudgetNs / 1000);
    }

    public static void resetRefinementWindow() {
        stableStartNs     = -1;
        stableFractionSum = 0;
        stableBudgetSum   = 0;
        stableSamples     = 0;
    }
}
