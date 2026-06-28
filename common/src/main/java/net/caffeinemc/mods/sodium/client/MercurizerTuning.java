package net.caffeinemc.mods.sodium.client;

import net.minecraft.client.Minecraft;
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

        double gpuBw   = result.bufferUploadBandwidthMBps > 0 ? Math.max(result.bufferUploadBandwidthMBps, 50.0) : 500.0;
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

        String source = result.isRefined ? "refined" : "synthetic";
        LOGGER.info("[Mercurizer] Tuning applied from {} benchmark results:", source);
        LOGGER.info("[Mercurizer]   Upload fraction:  Sodium default {}% -> Mercurizer {}%",
                String.format("%.0f", SODIUM_DEFAULT_UPLOAD_FRACTION * 100),
                String.format("%.1f", uploadFraction * 100));
        LOGGER.info("[Mercurizer]   Min upload budget: Sodium default {} ms -> Mercurizer {} ms",
                String.format("%.1f", SODIUM_DEFAULT_MIN_BUDGET_NS / 1_000_000.0),
                String.format("%.3f", minUploadBudgetNs / 1_000_000.0));
        LOGGER.info("[Mercurizer]   Texture anim throttle: {}  (GPU: {} MB/s, CPU: {} MOps/s)",
                texThrottleDesc,
                String.format("%.0f", result.bufferUploadBandwidthMBps),
                String.format("%.0f", result.cpuThroughputMOpsPerSec));
    }

    public static void checkRefinement() {
        if (!MercurizerFrameTracker.isRefinementReady()) return;
        if (lastResult == null) return;
        float refinedFraction = MercurizerFrameTracker.getRefinedFraction();
        long  refinedBudget   = MercurizerFrameTracker.getRefinedBudgetNs();
        if (refinedFraction <= 0 || refinedBudget <= 0) return;

        MercurizerBenchmarkResult refined = new MercurizerBenchmarkResult(
                lastResult.bufferUploadBandwidthMBps,
                lastResult.smallBufferUploadBandwidthMBps,
                lastResult.cpuThroughputMOpsPerSec,
                lastResult.availableProcessors,
                lastResult.rendererAtBenchmarkTime,
                lastResult.driverVersionAtBenchmarkTime,
                System.currentTimeMillis(),
                true);

        uploadFraction    = refinedFraction;
        minUploadBudgetNs = refinedBudget;
        lastResult        = refined;

        MercurizerBenchmarkStore.save(refined, Minecraft.getInstance().gameDirectory);
        MercurizerFrameTracker.markRefined();

        LOGGER.info("[Mercurizer] Adaptive refinement complete after 60s stable gameplay:");
        LOGGER.info("[Mercurizer]   Upload fraction refined to {}%", String.format("%.1f", refinedFraction * 100));
        LOGGER.info("[Mercurizer]   Min upload budget refined to {} ms", String.format("%.3f", refinedBudget / 1_000_000.0));
    }

    public static float getUploadFraction()                  { return MercurizerFrameTracker.getDynamicUploadFraction(uploadFraction); }
    public static long  getMinUploadBudgetNs()               { return MercurizerFrameTracker.getDynamicMinBudgetNs(minUploadBudgetNs); }
    public static long  getTextureAnimThresholdNs()          { return textureAnimThresholdNs; }
    public static MercurizerBenchmarkResult getLastResult()  { return lastResult; }
}
