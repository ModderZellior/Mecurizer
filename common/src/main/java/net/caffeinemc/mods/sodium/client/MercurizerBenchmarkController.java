package net.caffeinemc.mods.sodium.client;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public final class MercurizerBenchmarkController {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mercurizer");
    private static volatile boolean doneThisSession = false;
    public static int startupRunNumber = 0;

    private MercurizerBenchmarkController() {}

    public static boolean shouldShowBenchmarkScreen(Minecraft mc) {
        if (doneThisSession) return false;
        MercurizerCapabilities caps = MercurizerCapabilities.probeAndCache();
        MercurizerBenchmarkStore.setActiveRenderer(caps == null);
        File gameDir = mc.gameDirectory;

        if (caps == null) {
            MercurizerVulkanProbe.probe(); // logs device info on first call
            MercurizerBenchmarkResult latest = MercurizerBenchmarkStore.loadLatest(gameDir);
            if (latest != null && "Vulkan".equals(latest.driverVersionAtBenchmarkTime)) {
                int count = MercurizerBenchmarkStore.loadGameStartCount(gameDir);
                if (count < 3) {
                    MercurizerBenchmarkStore.incrementGameStartCount(gameDir);
                    startupRunNumber = count + 1;
                    MercurizerBenchmarkResult weighted = MercurizerBenchmarkStore.load(gameDir);
                    MercurizerTuning.apply(weighted != null ? weighted : latest, gameDir);
                    MercurizerTuning.setLatestRaw(latest);
                    LOGGER.info("[Mercurizer] Vulkan startup accuracy run {} of 3", startupRunNumber);
                    return true;
                }
                MercurizerBenchmarkResult weighted = MercurizerBenchmarkStore.load(gameDir);
                MercurizerTuning.apply(weighted != null ? weighted : latest, gameDir);
                MercurizerTuning.setLatestRaw(latest);
                LOGGER.info("[Mercurizer] Loaded stored Vulkan benchmark — CPU: {} MOps/s x {} cores",
                        String.format("%.0f", latest.cpuThroughputMOpsPerSec), latest.availableProcessors);
                doneThisSession = true;
                return false;
            }
            startupRunNumber = 1;
            return true;
        }

        if (MercurizerBenchmarkStore.needsBenchmark(caps, gameDir)) {
            // GPU/driver changed: reset 3-run startup sequence
            if (MercurizerBenchmarkStore.loadGameStartCount(gameDir) > 0) {
                MercurizerBenchmarkStore.resetStartupSequence(gameDir);
            }
            startupRunNumber = 1;
            MercurizerBenchmarkStore.incrementGameStartCount(gameDir);
            LOGGER.info("[Mercurizer] New/changed GPU — starting 3-run accuracy sequence (GPU: {})", caps.renderer);
            return true;
        }

        // GPU unchanged: check if we need more startup accuracy runs
        int count = MercurizerBenchmarkStore.loadGameStartCount(gameDir);
        if (count < 3) {
            MercurizerBenchmarkStore.incrementGameStartCount(gameDir);
            startupRunNumber = count + 1;
            MercurizerBenchmarkResult weighted = MercurizerBenchmarkStore.load(gameDir);
            MercurizerBenchmarkResult latest = MercurizerBenchmarkStore.loadLatest(gameDir);
            if (weighted != null || latest != null) {
                MercurizerTuning.apply(weighted != null ? weighted : latest, gameDir);
                MercurizerTuning.setLatestRaw(latest != null ? latest : weighted);
            }
            LOGGER.info("[Mercurizer] Startup accuracy run {} of 3", startupRunNumber);
            return true;
        }

        // 3 runs complete: load and apply stored results normally
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
