package net.caffeinemc.mods.sodium.client;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public final class MercurizerBenchmarkController {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mercurizer");
    private static volatile boolean doneThisSession = false;

    private MercurizerBenchmarkController() {}

    public static boolean shouldShowBenchmarkScreen(Minecraft mc) {
        if (doneThisSession) return false;
        MercurizerCapabilities caps = MercurizerCapabilities.probeAndCache();
        File gameDir = mc.gameDirectory;

        if (caps == null) {
            MercurizerBenchmarkResult latest = MercurizerBenchmarkStore.loadLatest(gameDir);
            if (latest != null && "Vulkan".equals(latest.rendererAtBenchmarkTime)) {
                MercurizerBenchmarkResult weighted = MercurizerBenchmarkStore.load(gameDir);
                MercurizerTuning.apply(weighted != null ? weighted : latest, gameDir);
                MercurizerTuning.setLatestRaw(latest);
                LOGGER.info("[Mercurizer] Loaded stored Vulkan benchmark — CPU: {} MOps/s x {} cores",
                        String.format("%.0f", latest.cpuThroughputMOpsPerSec), latest.availableProcessors);
                doneThisSession = true;
                return false;
            }
            return true;
        }

        if (!MercurizerBenchmarkStore.needsBenchmark(caps, gameDir)) {
            MercurizerBenchmarkResult latest   = MercurizerBenchmarkStore.loadLatest(gameDir);
            MercurizerBenchmarkResult weighted = MercurizerBenchmarkStore.load(gameDir);
            if (weighted != null || latest != null) {
                MercurizerBenchmarkResult toApply = weighted != null ? weighted : latest;
                LOGGER.info("[Mercurizer] Loaded stored benchmark results — GPU: {}/{} MB/s  CPU: {} MOps/s x {} cores",
                        String.format("%.0f", toApply.bufferUploadBandwidthMBps),
                        String.format("%.0f", toApply.smallBufferUploadBandwidthMBps),
                        String.format("%.0f", toApply.cpuThroughputMOpsPerSec),
                        toApply.availableProcessors);
                MercurizerTuning.apply(toApply, gameDir);
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

    public static void onBenchmarkComplete(MercurizerBenchmarkResult result) {
        File gameDir = Minecraft.getInstance().gameDirectory;
        MercurizerBenchmarkStore.saveWithHistory(result, gameDir);
        MercurizerTuning.apply(MercurizerBenchmarkStore.load(gameDir), gameDir);
        MercurizerTuning.setLatestRaw(result);
        doneThisSession = true;
    }
}
