package net.caffeinemc.mods.sodium.client;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public final class MercurizerTuning {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mercurizer");

    private static volatile MercurizerBenchmarkResult lastResult = null;
    private static volatile MercurizerBenchmarkResult latestRaw  = null;

    private static final long REFINEMENT_WINDOW_NS = 120_000_000_000L; // 2 minutes
    private static final int  REFINEMENT_SAMPLES   = 10;
    private static final float PLATEAU_TOLERANCE   = 0.05f;
    private static final int   PLATEAU_WINDOW      = 3;

    public static void setLatestRaw(MercurizerBenchmarkResult r) { latestRaw = r; }
    public static MercurizerBenchmarkResult getLatestRaw() { return latestRaw; }

    public static void apply(MercurizerBenchmarkResult result, File gameDirectory) {
        if (result == null) return;
        lastResult = result;

        if (result.isLowConfidence) {
            LOGGER.warn("[Mercurizer] Low confidence benchmark result (high variance) — tuning may be inaccurate");
        }

        double largeBw = result.bufferUploadBandwidthMBps;
        double smallBw = result.smallBufferUploadBandwidthMBps;
        double cpuMOps = result.cpuThroughputMOpsPerSec;
        int    cores   = result.availableProcessors;

        boolean hasGpu = largeBw > 0 && smallBw > 0;

        float uploadFraction;
        long  uploadBudgetNs;
        long  minUploadBudgetNs;

        if (!hasGpu) {
            uploadFraction    = Math.min(0.40f, 0.20f + (float)(cpuMOps / 500.0) * 0.10f);
            uploadBudgetNs    = 3_000_000L;
            minUploadBudgetNs = 500_000L;
        } else {
            double effectiveBw = Math.min(largeBw, smallBw * 1.5);
            uploadFraction = (float) Math.min(0.50f, Math.max(0.10f, effectiveBw / 10_000.0));
            double cpuScore = Math.min(1.0, cpuMOps / 1000.0) * Math.min(cores, 8) / 4.0;
            uploadFraction = Math.min(0.50f, uploadFraction * (float)(0.7 + cpuScore * 0.3));

            double targetMs = 2.0;
            if (smallBw > 0) targetMs = Math.max(1.0, Math.min(4.0, 128.0 / smallBw * 1000.0));
            uploadBudgetNs    = (long)(targetMs * 1_000_000.0);
            minUploadBudgetNs = 300_000L;
        }

        // Round-trip latency floor
        if (result.smallBufferRoundTripNs > 0 && result.smallBufferSizeBytes > 0) {
            double scaledNs = result.smallBufferRoundTripNs * (128_000.0 / result.smallBufferSizeBytes);
            long roundTripFloor = (long)(scaledNs * 2.0);
            if (roundTripFloor > minUploadBudgetNs) {
                minUploadBudgetNs = roundTripFloor;
                LOGGER.info("[Mercurizer] Round-trip floor: {} us", roundTripFloor / 1000);
            }
        }

        MercurizerFrameTracker.configure(uploadBudgetNs, uploadFraction, minUploadBudgetNs);
        LOGGER.info("[Mercurizer] Tuning applied — fraction={}, budget={}us, minBudget={}us",
                String.format("%.2f", uploadFraction),
                uploadBudgetNs / 1000,
                minUploadBudgetNs / 1000);
    }

    public static void checkRefinement(File gameDirectory) {
        MercurizerBenchmarkResult lastResult = MercurizerTuning.lastResult;
        if (lastResult == null) return;

        float uploadFraction = MercurizerFrameTracker.getUploadFraction();
        long  uploadBudgetNs = MercurizerFrameTracker.getUploadBudgetNs();

        // Accumulate stable samples
        long now = System.nanoTime();
        if (MercurizerFrameTracker.stableStartNs < 0) {
            MercurizerFrameTracker.stableStartNs = now;
        }

        MercurizerFrameTracker.stableFractionSum += uploadFraction;
        MercurizerFrameTracker.stableBudgetSum   += uploadBudgetNs;
        MercurizerFrameTracker.stableSamples++;

        if (now - MercurizerFrameTracker.stableStartNs < REFINEMENT_WINDOW_NS) return;
        if (MercurizerFrameTracker.stableSamples < REFINEMENT_SAMPLES) {
            MercurizerFrameTracker.resetRefinementWindow();
            return;
        }

        float refinedFraction = MercurizerFrameTracker.stableFractionSum / MercurizerFrameTracker.stableSamples;
        long  refinedBudget   = (long)(MercurizerFrameTracker.stableBudgetSum / MercurizerFrameTracker.stableSamples);
        MercurizerFrameTracker.resetRefinementWindow();

        // Thermal throttle guard
        if (refinedFraction < uploadFraction * 0.70f) {
            LOGGER.warn("[Mercurizer] Refinement skipped — fraction dropped too far (thermal throttle suspected)");
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

        MercurizerBenchmarkStore.updateLatestRefined(refined, gameDirectory);
        MercurizerTuning.lastResult = refined;
        LOGGER.info("[Mercurizer] Refined tuning saved — fraction={}, budget={}us",
                String.format("%.2f", refinedFraction), refinedBudget / 1000);
    }
}
