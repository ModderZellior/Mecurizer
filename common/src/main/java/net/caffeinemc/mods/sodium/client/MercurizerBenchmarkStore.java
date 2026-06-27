package net.caffeinemc.mods.sodium.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

public final class MercurizerBenchmarkStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mercurizer");
    private static final String DIR = "mercurizer";
    private static final String FILE = "benchmark.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static boolean needsBenchmark(MercurizerCapabilities caps, File gameDirectory) {
        MercurizerBenchmarkResult stored = load(gameDirectory);
        if (stored == null) return true;
        // Force re-benchmark if CPU data is missing (old benchmark.json from before CPU support)
        if (stored.cpuThroughputMOpsPerSec <= 0) return true;
        return !caps.renderer.equals(stored.rendererAtBenchmarkTime) ||
               !caps.version.equals(stored.driverVersionAtBenchmarkTime);
    }

    public static void save(MercurizerBenchmarkResult result, File gameDirectory) {
        File dir = new File(gameDirectory, DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            LOGGER.warn("[Mercurizer] Could not create directory: {}", dir);
            return;
        }
        File file = new File(dir, FILE);
        try (Writer writer = new FileWriter(file)) {
            GSON.toJson(result, writer);
        } catch (IOException e) {
            LOGGER.warn("[Mercurizer] Could not save benchmark results: {}", e.getMessage());
        }
    }

    public static MercurizerBenchmarkResult load(File gameDirectory) {
        File file = new File(new File(gameDirectory, DIR), FILE);
        if (!file.exists()) return null;
        try (Reader reader = new FileReader(file)) {
            return GSON.fromJson(reader, MercurizerBenchmarkResult.class);
        } catch (Exception e) {
            return null;
        }
    }
}
