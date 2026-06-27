package net.caffeinemc.mods.sodium.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MercurizerTuning {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mercurizer");

    // Sodium defaults (what Sodium uses without Mercurizer)
    private static final float SODIUM_DEFAULT_UPLOAD_FRACTION  = 0.1f;
    private static final long  SODIUM_DEFAULT_MIN_BUDGET_NS    = 2_000_000L;

    // Defaults hold until benchmark results are applied
    private static volatile float uploadFraction           = 0.06f;
    private static volatile long  minUploadBudgetNs        = 1_000_000L;
    private static volatile long  textureAnimThresholdNs   = Long.MAX_VALUE; // always animate until benchmarked
    private static volatile MercurizerBenchmarkResult lastResult;

    private MercurizerTuning() {}

    public static void apply(MercurizerBenchmarkResult result) {
        lastResult = result;

        double gpuBw   = Math.max(result.bufferUploadBandwidthMBps, 100.0);
        double cpuMOps = Math.max(result.cpuThroughputMOpsPerSec, 50.0);

        // GPU factor: a faster GPU drains the upload queue more cheaply per unit of frame time,
        // so it needs a smaller fraction. Slower GPUs need more frame time to keep up.
        double gpuFactor = 1500.0 / gpuBw;

        // CPU factor: a faster CPU fills the chunk build queue more aggressively, so we
        // should upload more per frame to avoid a backlog. Baseline ~1000 MOps/s.
        double cpuFactor = Math.max(0.6, Math.min(1.6, cpuMOps / 1000.0));

        uploadFraction = (float) Math.max(0.03, Math.min(0.10, 0.06 * gpuFactor * cpuFactor));

        // Min budget: enough time to stream at least one typical chunk section (~512 KB)
        // regardless of how tight the frame budget is.
        long sectionNs = (long) ((512_000.0 / (gpuBw * 1024.0 * 1024.0)) * 1_000_000_000.0);
        minUploadBudgetNs = Math.max(500_000L, Math.min(3_000_000L, sectionNs));

        // Texture animation throttle threshold: on slow CPUs skip texture animation when
        // frames are already long to free up CPU time for chunk building and game logic.
        // Fast CPUs (>=1000 MOps/s): always animate.
        // Medium CPUs (500-1000 MOps/s): skip animation if frame > 30 ms.
        // Slow CPUs (<500 MOps/s): skip animation if frame > 22 ms.
        String texThrottleDesc;
        if (cpuMOps >= 1000.0) {
            textureAnimThresholdNs = Long.MAX_VALUE;
            texThrottleDesc = "disabled (fast CPU)";
        } else if (cpuMOps >= 500.0) {
            textureAnimThresholdNs = 30_000_000L;
            texThrottleDesc = "30 ms threshold";
        } else {
            textureAnimThresholdNs = 22_000_000L;
            texThrottleDesc = "22 ms threshold";
        }

        LOGGER.info("[Mercurizer] Tuning applied from benchmark results:");
        LOGGER.info("[Mercurizer]   Upload fraction:  Sodium default {}% -> Mercurizer {}%",
                String.format("%.0f", SODIUM_DEFAULT_UPLOAD_FRACTION * 100),
                String.format("%.0f", uploadFraction * 100));
        LOGGER.info("[Mercurizer]   Min upload budget: Sodium default {} ms -> Mercurizer {} ms",
                String.format("%.1f", SODIUM_DEFAULT_MIN_BUDGET_NS / 1_000_000.0),
                String.format("%.2f", minUploadBudgetNs / 1_000_000.0));
        LOGGER.info("[Mercurizer]   Texture anim throttle: {}  (GPU: {} MB/s, CPU: {} MOps/s)",
                texThrottleDesc,
                String.format("%.0f", result.bufferUploadBandwidthMBps),
                String.format("%.0f", result.cpuThroughputMOpsPerSec));
    }

    public static float getUploadFraction()              { return uploadFraction; }
    public static long  getMinUploadBudgetNs()           { return minUploadBudgetNs; }
    public static long  getTextureAnimThresholdNs()      { return textureAnimThresholdNs; }
    public static MercurizerBenchmarkResult getLastResult() { return lastResult; }
}
