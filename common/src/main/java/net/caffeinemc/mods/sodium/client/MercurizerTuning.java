package net.caffeinemc.mods.sodium.client;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MercurizerTuning {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mercurizer");

    private static volatile float uploadFraction = 0.04f;
    private static volatile long minUploadBudgetNs = 500_000L;
    private static volatile long textureAnimThresholdNs = Long.MAX_VALUE;
    private static volatile MercurizerBenchmarkResult lastResult;
    private static volatile MercurizerBenchmarkResult latestRaw;
    private static volatile MercurizerBenchmarkResult appliedResult;
    private static volatile float refinedUploadFraction = -1;
    private static volatile long refinedMinBudgetNs = -1;

    private MercurizerTuning() {}

    public static void apply(MercurizerBenchmarkResult benchmark) {
        apply(benchmark, true);
    }

    private static void apply(MercurizerBenchmarkResult benchmark, boolean announce) {
        lastResult = benchmark;
        appliedResult = benchmark;
        refinedUploadFraction = -1;
        refinedMinBudgetNs = -1;
        MercurizerBenchmarkResult result = MercurizerConfig.withMeasurementOverrides(benchmark);

        double chunkBw = result.smallBufferUploadBandwidthMBps > 0 ? result.smallBufferUploadBandwidthMBps
                       : (result.bufferUploadBandwidthMBps > 0 ? result.bufferUploadBandwidthMBps : 500.0);
        chunkBw = Math.max(chunkBw, 50.0);
        double cpuMOps = Math.max(result.cpuThroughputMOpsPerSec, 50.0);

        double cpuNorm = Math.max(0.5, Math.min(1.75, cpuMOps / 800.0));
        uploadFraction = (float) Math.max(0.02, Math.min(0.07, 0.04 * cpuNorm));
        if (result.isLowConfidence) {
            MercurizerCapabilities caps = MercurizerCapabilities.getCached();
            if (caps != null) uploadFraction *= gpuMultiplier(caps.renderer);
        }

        long oneChunkNs = (long)(131_072_000.0 / chunkBw);
        minUploadBudgetNs = Math.max(150_000L, Math.min(1_000_000L, oneChunkNs));

        // Floor at 2× round-trip latency, scaled to 128KB chunk size
        if (result.smallBufferRoundTripNs > 0 && result.smallBufferSizeBytes > 0) {
            double scaledNs = result.smallBufferRoundTripNs * (128_000.0 / result.smallBufferSizeBytes);
            long roundTripFloor = (long) (scaledNs * 2.0);
            if (roundTripFloor > minUploadBudgetNs) {
                if (announce) LOGGER.info("[Mercurizer]   Round-trip floor raised min budget: {} ms -> {} ms",
                        String.format("%.3f", minUploadBudgetNs / 1_000_000.0),
                        String.format("%.3f", roundTripFloor / 1_000_000.0));
                minUploadBudgetNs = roundTripFloor;
            }
        }

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

        if (!announce) return;
        String source = result.isRefined ? "refined" : "synthetic";
        LOGGER.info("[Mercurizer] Tuning applied from {} benchmark results:", source);
        LOGGER.info("[Mercurizer]   Upload fraction:  Sodium default 10% -> Mercurizer {}%",
                String.format("%.1f", uploadFraction * 100));
        LOGGER.info("[Mercurizer]   Min upload budget: Sodium default 2.0 ms -> Mercurizer {} ms",
                String.format("%.3f", minUploadBudgetNs / 1_000_000.0));
        LOGGER.info("[Mercurizer]   Texture anim throttle: {}  (Chunk: {} MB/s, CPU: {} MOps/s)",
                texThrottleDesc,
                String.format("%.0f", chunkBw),
                String.format("%.0f", result.cpuThroughputMOpsPerSec));
        if (result.isLowConfidence) {
            LOGGER.warn("[Mercurizer]   Low-confidence benchmark (CoV {}) — tuning may be suboptimal",
                    String.format("%.0f%%", result.benchmarkConfidenceScore * 100));
        }
    }

    public static void reapply() {
        MercurizerFrameTracker.resetDynamicValues();
        MercurizerBenchmarkResult benchmark = appliedResult;
        if (benchmark == null) return;
        MercurizerBenchmarkResult current = lastResult;
        float keptFraction = refinedUploadFraction;
        long keptBudget = refinedMinBudgetNs;
        apply(benchmark, false);
        if (keptFraction > 0 && !MercurizerConfig.hasMeasurementOverrides()) {
            uploadFraction = keptFraction;
            minUploadBudgetNs = keptBudget;
            refinedUploadFraction = keptFraction;
            refinedMinBudgetNs = keptBudget;
            lastResult = current;
        }
    }

    public static void checkRefinement() {
        if (!MercurizerConfig.isEnabled(MercurizerConfig.get().adaptiveRefinement)) return;
        if (MercurizerConfig.hasMeasurementOverrides()) return;
        if (!MercurizerFrameTracker.isRefinementReady()) return;
        if (lastResult == null) return;
        float refinedFraction = MercurizerFrameTracker.getRefinedFraction();
        long refinedBudget = MercurizerFrameTracker.getRefinedBudgetNs();
        if (refinedFraction <= 0 || refinedBudget <= 0) return;

        // Thermal throttle detection — skip refinement if fraction dropped >30% below current
        if (refinedFraction < uploadFraction * 0.70f) {
            LOGGER.warn("[Mercurizer] Refinement skipped — refined fraction ({}) is >30% below benchmark ({}) — possible thermal throttle",
                    String.format("%.2f%%", refinedFraction * 100),
                    String.format("%.2f%%", uploadFraction * 100));
            MercurizerFrameTracker.resetRefinementWindow();
            return;
        }

        MercurizerBenchmarkResult refined = new MercurizerBenchmarkResult(
                lastResult.bufferUploadBandwidthMBps,
                lastResult.smallBufferUploadBandwidthMBps,
                lastResult.cpuThroughputMOpsPerSec,
                lastResult.availableProcessors,
                lastResult.rendererAtBenchmarkTime,
                lastResult.driverVersionAtBenchmarkTime,
                System.currentTimeMillis(),
                true,
                lastResult.smallBufferRoundTripNs,
                lastResult.benchmarkConfidenceScore,
                lastResult.isLowConfidence,
                lastResult.largeBufferSizeBytes,
                lastResult.smallBufferSizeBytes);

        uploadFraction = refinedFraction;
        minUploadBudgetNs = refinedBudget;
        refinedUploadFraction = refinedFraction;
        refinedMinBudgetNs = refinedBudget;
        lastResult = refined;

        MercurizerBenchmarkStore.updateLatestRefined(refined, Minecraft.getInstance().gameDirectory);
        MercurizerFrameTracker.markRefined();

        LOGGER.info("[Mercurizer] Adaptive refinement complete after 30s stable gameplay:");
        LOGGER.info("[Mercurizer]   Upload fraction refined to {}%", String.format("%.1f", refinedFraction * 100));
        LOGGER.info("[Mercurizer]   Min upload budget refined to {} ms", String.format("%.3f", refinedBudget / 1_000_000.0));
    }

    public static void setLatestRaw(MercurizerBenchmarkResult r) { latestRaw = r; }
    public static MercurizerBenchmarkResult getLatestRaw() { return latestRaw != null ? latestRaw : lastResult; }

    public static float getUploadFraction() {
        MercurizerConfig.Values config = MercurizerConfig.get();
        float base = getBaseUploadFraction();
        float f = MercurizerConfig.isEnabled(config.dynamicUploadScaling)
                ? MercurizerFrameTracker.getDynamicUploadFraction(base)
                : base;
        if (MercurizerConfig.isEnabled(config.antiStutter) && MercurizerFrameTracker.isRecovering()) {
            f = Math.min(f, base * 0.50f);
        }
        Minecraft mc = Minecraft.getInstance();
        if (MercurizerConfig.isEnabled(config.vsyncConservative)
                && mc != null && Boolean.TRUE.equals(mc.options.enableVsync().get())) {
            f *= 0.80f;
        }
        return f;
    }

    public static long getMinUploadBudgetNs() {
        long base = getBaseMinUploadBudgetNs();
        if (!MercurizerConfig.isEnabled(MercurizerConfig.get().dynamicUploadScaling)) return base;
        return MercurizerFrameTracker.getDynamicMinBudgetNs(base);
    }

    public static float getBaseUploadFraction() {
        Float percent = MercurizerConfig.get().uploadFractionPercent;
        return percent != null ? percent / 100f : uploadFraction;
    }

    public static long getBaseMinUploadBudgetNs() {
        Float ms = MercurizerConfig.get().minUploadBudgetMs;
        return ms != null ? (long) (ms * 1_000_000L) : minUploadBudgetNs;
    }

    public static long getTextureAnimThresholdNs() {
        Integer ms = MercurizerConfig.get().textureAnimThresholdMs;
        if (ms == null) return textureAnimThresholdNs;
        return ms > MercurizerConfig.TEXTURE_THRESHOLD_MAX_MS ? Long.MAX_VALUE : ms * 1_000_000L;
    }

    public static float getBenchmarkUploadFraction() { return uploadFraction; }
    public static long getBenchmarkMinUploadBudgetNs() { return minUploadBudgetNs; }
    public static long getBenchmarkTextureAnimThresholdNs() { return textureAnimThresholdNs; }
    public static MercurizerBenchmarkResult getAppliedResult() { return appliedResult; }
    public static MercurizerBenchmarkResult getLastResult() { return lastResult; }

    private static float gpuMultiplier(String renderer) {
        if (renderer == null) return 1.0f;
        String r = renderer.toLowerCase();
        if (r.contains("llvmpipe")) return 0.50f;
        if (r.contains("softpipe")) return 0.45f;
        if (r.contains("intel")) return 0.70f;
        return 1.0f;
    }
}
