package net.caffeinemc.mods.sodium.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MercurizerTuning {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mercurizer");

    private static final float SODIUM_DEFAULT_UPLOAD_FRACTION = 0.1f;
    private static final long  SODIUM_DEFAULT_MIN_BUDGET_NS   = 2_000_000L;

    private static volatile float uploadFraction         = 0.04f;
    private static volatile long  minUploadBudgetNs      = 500_000L;
    private static volatile long  textureAnimThresholdNs = Long.MAX_VALUE;
    private static volatile MercurizerBenchmarkResult lastResult;

    private MercurizerTuning() {}

    public static void apply(MercurizerBenchmarkResult result) {
        lastResult = result;

        boolean vulkan = result.bufferUploadBandwidthMBps <= 0;
        double gpuBw   = vulkan ? 500.0 : Math.max(result.bufferUploadBandwidthMBps, 50.0);
        double cpuMOps = Math.max(result.cpuThroughputMOpsPerSec, 50.0);

        double cpuNorm = Math.max(0.5, Math.min(1.75, cpuMOps / 800.0));
        uploadFraction = (float) Math.max(0.02, Math.min(0.07, 0.04 * cpuNorm));

        long oneChunkNs = (long) ((128_000.0 / (gpuBw * 1_048_576.0)) * 1_000_000_000.0);
        minUploadBudgetNs = Math.max(150_000L, Math.min(800_000L, oneChunkNs));

        String texThrottleDesc;
        if (cpuMOps >= 900.0) {
            textureAnimThresholdNs = Long.MAX_VALUE;
            texThrottleDesc = "always on";
        } else if (cpuMOps >= 450.0) {
            textureAnimThresholdNs = 22_000_000L;
            texThrottleDesc = "22 ms threshold";
        } else {
            textureAnimThresholdNs = 16_000_000L;
            texThrottleDesc = "16 ms threshold";
        }

        LOGGER.info("[Mercurizer] Tuning applied from benchmark results:");
        LOGGER.info("[Mercurizer]   Upload fraction:  Sodium default {}% -> Mercurizer {}%",
                String.format("%.0f", SODIUM_DEFAULT_UPLOAD_FRACTION * 100),
                String.format("%.1f", uploadFraction * 100));
        LOGGER.info("[Mercurizer]   Min upload budget: Sodium default {} ms -> Mercurizer {} ms",
                String.format("%.1f", SODIUM_DEFAULT_MIN_BUDGET_NS / 1_000_000.0),
                String.format("%.3f", minUploadBudgetNs / 1_000_000.0));
        LOGGER.info("[Mercurizer]   Texture anim throttle: {}  (backend: {}, CPU: {} MOps/s)",
                texThrottleDesc,
                vulkan ? "Vulkan" : String.format("%.0f MB/s GPU", result.bufferUploadBandwidthMBps),
                String.format("%.0f", result.cpuThroughputMOpsPerSec));
    }

    public static float getUploadFraction()                  { return uploadFraction; }
    public static long  getMinUploadBudgetNs()               { return minUploadBudgetNs; }
    public static long  getTextureAnimThresholdNs()          { return textureAnimThresholdNs; }
    public static MercurizerBenchmarkResult getLastResult()  { return lastResult; }
}
