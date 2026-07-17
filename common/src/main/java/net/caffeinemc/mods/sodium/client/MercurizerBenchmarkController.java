package net.caffeinemc.mods.sodium.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public final class MercurizerBenchmarkController {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mercurizer");

    public static void checkAndRun(Screen currentScreen) {
        MercurizerCapabilities caps = MercurizerCapabilities.detect();
        File gameDir = Minecraft.getInstance().gameDirectory;

        // Vulkan path: GPU benchmark meaningless, use CPU-only result
        if (caps == null) {
            LOGGER.info("[Mercurizer] Vulkan/non-GL backend detected, running CPU-only benchmark");
            MercurizerBenchmarkResult result = MercurizerBenchmark.run(null);
            MercurizerBenchmarkStore.saveWithHistory(result, gameDir);
            MercurizerTuning.apply(MercurizerBenchmarkStore.load(gameDir), gameDir);
            MercurizerTuning.setLatestRaw(result);
            return;
        }

        if (!MercurizerBenchmarkStore.needsBenchmark(caps, gameDir)) {
            MercurizerBenchmarkResult latest = MercurizerBenchmarkStore.loadLatest(gameDir);
            MercurizerTuning.apply(MercurizerBenchmarkStore.load(gameDir), gameDir);
            MercurizerTuning.setLatestRaw(latest);
            LOGGER.info("[Mercurizer] Reusing existing benchmark result");
            return;
        }

        Minecraft.getInstance().setScreen(new MercurizerBenchmarkScreen(currentScreen));
    }

    public static void onBenchmarkComplete(MercurizerBenchmarkResult result) {
        File gameDir = Minecraft.getInstance().gameDirectory;
        MercurizerBenchmarkStore.saveWithHistory(result, gameDir);
        MercurizerTuning.apply(MercurizerBenchmarkStore.load(gameDir), gameDir);
        MercurizerTuning.setLatestRaw(result);
    }
}
