package net.caffeinemc.mods.sodium.client;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class MercurizerBenchmarkController {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mercurizer");
    private static boolean doneThisSession = false;
    public static int startupRunNumber = 0;

    private MercurizerBenchmarkController() {}

    public static boolean shouldShowBenchmarkScreen(Minecraft mc) {
        if (doneThisSession) return false;
        MercurizerCapabilities caps = MercurizerCapabilities.probeAndCache();

        if (caps == null) {
            // Vulkan backend — check if a Vulkan CPU-only benchmark was already stored
            MercurizerBenchmarkResult latest = MercurizerBenchmarkStore.loadLatest(mc.gameDirectory);
            if (latest != null && "Vulkan".equals(latest.rendererAtBenchmarkTime)) {
                MercurizerBenchmarkResult weighted = MercurizerBenchmarkStore.load(mc.gameDirectory);
                MercurizerBenchmarkResult toApply = weighted != null ? weighted : latest;
                LOGGER.info("[Mercurizer] Loaded stored Vulkan benchmark — CPU: {} MOps/s x {} cores",
                        String.format("%.0f", latest.cpuThroughputMOpsPerSec), latest.availableProcessors);
                MercurizerTuning.apply(toApply);
                MercurizerTuning.setLatestRaw(latest);
                doneThisSession = true;
                return false;
            }
            LOGGER.info("[Mercurizer] No stored Vulkan benchmark — will run CPU benchmark now");
            startupRunNumber = 1;
            return true;
        }

        if (MercurizerBenchmarkStore.needsBenchmark(caps, mc.gameDirectory)) {
            // GPU/driver changed: reset 3-run startup sequence
            if (MercurizerBenchmarkStore.loadGameStartCount(mc.gameDirectory) > 0) {
                MercurizerBenchmarkStore.resetStartupSequence(mc.gameDirectory);
            }
            startupRunNumber = 1;
            MercurizerBenchmarkStore.incrementGameStartCount(mc.gameDirectory);
            LOGGER.info("[Mercurizer] New/changed GPU — starting 3-run accuracy sequence (GPU: {})", caps.renderer);
            return true;
        }

        // GPU unchanged: check if we need more startup accuracy runs
        int count = MercurizerBenchmarkStore.loadGameStartCount(mc.gameDirectory);
        if (count < 3) {
            MercurizerBenchmarkStore.incrementGameStartCount(mc.gameDirectory);
            startupRunNumber = count + 1;
            // Apply existing results so game is tuned while benchmarking
            MercurizerBenchmarkResult weighted = MercurizerBenchmarkStore.load(mc.gameDirectory);
            MercurizerBenchmarkResult latest   = MercurizerBenchmarkStore.loadLatest(mc.gameDirectory);
            if (weighted != null || latest != null) {
                MercurizerTuning.apply(weighted != null ? weighted : latest);
                MercurizerTuning.setLatestRaw(latest != null ? latest : weighted);
            }
            LOGGER.info("[Mercurizer] Startup accuracy run {} of 3", startupRunNumber);
            return true;
        }

        // 3 runs complete: load and apply stored results normally
        MercurizerBenchmarkResult weighted = MercurizerBenchmarkStore.load(mc.gameDirectory);
        MercurizerBenchmarkResult latest   = MercurizerBenchmarkStore.loadLatest(mc.gameDirectory);
        if (weighted != null) {
            LOGGER.info("[Mercurizer] Loaded stored benchmark results — GPU: {}/{} MB/s  CPU: {} MOps/s x {} cores",
                    String.format("%.0f", weighted.bufferUploadBandwidthMBps),
                    String.format("%.0f", weighted.smallBufferUploadBandwidthMBps),
                    String.format("%.0f", weighted.cpuThroughputMOpsPerSec),
                    weighted.availableProcessors);
            MercurizerTuning.apply(weighted);
            MercurizerTuning.setLatestRaw(latest != null ? latest : weighted);
        }
        doneThisSession = true;
        return false;
    }

    public static void markDone() {
        doneThisSession = true;
    }

    public static void resetForRebenchmark() {
        doneThisSession = false;
        MercurizerCapabilities.clearCache();
    }
}
