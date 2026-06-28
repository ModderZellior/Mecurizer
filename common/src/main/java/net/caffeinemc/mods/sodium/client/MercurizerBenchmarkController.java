package net.caffeinemc.mods.sodium.client;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public final class MercurizerBenchmarkController {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mercurizer");
    private static boolean doneThisSession = false;

    private MercurizerBenchmarkController() {}

    public static boolean shouldShowBenchmarkScreen(Minecraft mc) {
        if (doneThisSession) return false;
        MercurizerCapabilities caps = MercurizerCapabilities.probeAndCache();
        if (!MercurizerBenchmarkStore.needsBenchmark(caps, mc.gameDirectory)) {
            MercurizerBenchmarkResult stored = MercurizerBenchmarkStore.load(mc.gameDirectory);
            if (stored != null) {
                LOGGER.info("[Mercurizer] Loaded stored benchmark results — GPU: {}/{} MB/s  CPU: {} MOps/s x {} cores",
                        String.format("%.0f", stored.bufferUploadBandwidthMBps),
                        String.format("%.0f", stored.smallBufferUploadBandwidthMBps),
                        String.format("%.0f", stored.cpuThroughputMOpsPerSec),
                        stored.availableProcessors);
                MercurizerTuning.apply(stored);
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
        File f = new File(new File(Minecraft.getInstance().gameDirectory, "mercurizer"), "benchmark.json");
        f.delete();
    }
}
