package net.caffeinemc.mods.sodium.client;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class MercurizerBenchmarkController {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mercurizer");
    private static boolean doneThisSession = false;

    private MercurizerBenchmarkController() {}

    public static boolean shouldShowBenchmarkScreen(Minecraft mc) {
        if (doneThisSession) return false;
        MercurizerCapabilities caps = MercurizerCapabilities.probeAndCache();
        if (caps == null) {
            // Vulkan backend — check if a Vulkan CPU-only benchmark was already stored
            MercurizerBenchmarkResult latest = MercurizerBenchmarkStore.loadLatest(mc.gameDirectory);
            if (latest != null && "Vulkan".equals(latest.rendererAtBenchmarkTime)) {
                // Apply weighted average of stored history
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
            return true;
        }
        if (!MercurizerBenchmarkStore.needsBenchmark(caps, mc.gameDirectory)) {
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
        LOGGER.info("[Mercurizer] No valid stored benchmark found — will run benchmark now (GPU: {})", caps.renderer);
        return true;
    }

    public static void markDone() {
        doneThisSession = true;
    }

    public static void resetForRebenchmark() {
        doneThisSession = false;
        MercurizerCapabilities.clearCache();
    }
}
