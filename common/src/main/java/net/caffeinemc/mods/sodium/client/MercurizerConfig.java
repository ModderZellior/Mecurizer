package net.caffeinemc.mods.sodium.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

public final class MercurizerConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mercurizer");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DIR = "mercurizer";
    private static final String FILE = "config.json";

    public static final int TEXTURE_THRESHOLD_MIN_MS = 8;
    public static final int TEXTURE_THRESHOLD_MAX_MS = 50;

    public static final class Values {
        public Double gpuLargeMBps;
        public Double gpuSmallMBps;
        public Double cpuMOps;
        public Double roundTripMs;
        public Float uploadFractionPercent;
        public Float minUploadBudgetMs;
        public Integer textureAnimThresholdMs;
        public Boolean dynamicUploadScaling;
        public Boolean antiStutter;
        public Boolean vsyncConservative;
        public Boolean adaptiveRefinement;
        public Integer chunkBuilderThreads;
        public Boolean safeStagingPath;

        public boolean isEmpty() {
            return gpuLargeMBps == null && gpuSmallMBps == null && cpuMOps == null && roundTripMs == null
                    && uploadFractionPercent == null && minUploadBudgetMs == null && textureAnimThresholdMs == null
                    && dynamicUploadScaling == null && antiStutter == null && vsyncConservative == null
                    && adaptiveRefinement == null && chunkBuilderThreads == null && safeStagingPath == null;
        }
    }

    private static Values values = load();

    private MercurizerConfig() {}

    public static Values get() {
        return values;
    }

    public static void update(Values updated) {
        values = updated;
        MercurizerTuning.reapply();
    }

    public static boolean hasMeasurementOverrides() {
        Values v = values;
        return v.gpuLargeMBps != null || v.gpuSmallMBps != null || v.cpuMOps != null || v.roundTripMs != null;
    }

    public static MercurizerBenchmarkResult withMeasurementOverrides(MercurizerBenchmarkResult result) {
        if (result == null || !hasMeasurementOverrides()) {
            return result;
        }
        Values v = values;
        return new MercurizerBenchmarkResult(
                v.gpuLargeMBps != null ? v.gpuLargeMBps : result.bufferUploadBandwidthMBps,
                v.gpuSmallMBps != null ? v.gpuSmallMBps : result.smallBufferUploadBandwidthMBps,
                v.cpuMOps != null ? v.cpuMOps : result.cpuThroughputMOpsPerSec,
                result.availableProcessors,
                result.rendererAtBenchmarkTime,
                result.driverVersionAtBenchmarkTime,
                result.benchmarkTimestamp,
                result.isRefined,
                v.roundTripMs != null ? v.roundTripMs * 1_000_000.0 : result.smallBufferRoundTripNs,
                result.benchmarkConfidenceScore,
                result.isLowConfidence,
                result.largeBufferSizeBytes,
                result.smallBufferSizeBytes);
    }

    public static boolean isEnabled(Boolean override) {
        return override == null || override;
    }

    private static File file() {
        Minecraft mc = Minecraft.getInstance();
        File gameDirectory = mc != null ? mc.gameDirectory : new File(".");
        return new File(new File(gameDirectory, DIR), FILE);
    }

    private static Values load() {
        File file = file();
        if (!file.exists()) return new Values();
        try (Reader reader = new FileReader(file)) {
            Values loaded = GSON.fromJson(reader, Values.class);
            return loaded != null ? loaded : new Values();
        } catch (Exception e) {
            LOGGER.warn("[Mercurizer] Could not read config.json, using benchmark values: {}", e.getMessage());
            return new Values();
        }
    }

    public static void save() {
        File file = file();
        if (values.isEmpty()) {
            if (file.exists() && !file.delete()) {
                LOGGER.warn("[Mercurizer] Could not remove {}", file);
            }
            return;
        }
        File dir = file.getParentFile();
        if (!dir.exists() && !dir.mkdirs()) {
            LOGGER.warn("[Mercurizer] Could not create directory: {}", dir);
            return;
        }
        try (Writer writer = new FileWriter(file)) {
            GSON.toJson(values, writer);
        } catch (IOException e) {
            LOGGER.warn("[Mercurizer] Could not save config.json: {}", e.getMessage());
        }
    }
}
