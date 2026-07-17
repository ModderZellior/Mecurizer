package net.caffeinemc.mods.sodium.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public final class MercurizerBenchmarkStore {
    private static final Logger LOGGER   = LoggerFactory.getLogger("Mercurizer");
    private static final String DIR      = "mercurizer";
    private static final String FILE     = "benchmark.json";
    private static final int    MAX_HISTORY = 3;
    private static final Gson   GSON     = new GsonBuilder().setPrettyPrinting().create();

    // Weights for multi-session averaging: newest first
    private static final double[] WEIGHTS = { 0.5, 0.3, 0.2 };

    private static class HistoryWrapper {
        List<MercurizerBenchmarkResult> history = new ArrayList<>();
    }

    public static boolean needsBenchmark(MercurizerCapabilities caps, File gameDirectory) {
        MercurizerBenchmarkResult latest = loadLatest(gameDirectory);
        if (latest == null) return true;
        if (latest.cpuThroughputMOpsPerSec <= 0) return true;
        return !caps.renderer.equals(latest.rendererAtBenchmarkTime) ||
               !caps.version.equals(latest.driverVersionAtBenchmarkTime);
    }

    /** Saves result prepended to history (keeps last 3). */
    public static void saveWithHistory(MercurizerBenchmarkResult result, File gameDirectory) {
        List<MercurizerBenchmarkResult> history = loadHistory(gameDirectory);
        history.add(0, result);
        if (history.size() > MAX_HISTORY) history = history.subList(0, MAX_HISTORY);
        writeHistory(history, gameDirectory);
    }

    /** Updates the most recent history entry in place (for refinement — same session, not a new benchmark). */
    public static void updateLatestRefined(MercurizerBenchmarkResult refined, File gameDirectory) {
        List<MercurizerBenchmarkResult> history = loadHistory(gameDirectory);
        if (!history.isEmpty()) {
            history.set(0, refined);
        } else {
            history.add(refined);
        }
        writeHistory(history, gameDirectory);
    }

    /** Legacy save — prepends to history. */
    public static void save(MercurizerBenchmarkResult result, File gameDirectory) {
        saveWithHistory(result, gameDirectory);
    }

    /** Returns weighted average of history for use in tuning, or null if no history. */
    public static MercurizerBenchmarkResult load(File gameDirectory) {
        List<MercurizerBenchmarkResult> history = loadHistory(gameDirectory);
        if (history.isEmpty()) return null;
        return computeWeightedAverage(history);
    }

    /** Returns the most recent raw result for display in the info screen. */
    public static MercurizerBenchmarkResult loadLatest(File gameDirectory) {
        List<MercurizerBenchmarkResult> history = loadHistory(gameDirectory);
        return history.isEmpty() ? null : history.get(0);
    }

    public static List<MercurizerBenchmarkResult> loadHistory(File gameDirectory) {
        File file = new File(new File(gameDirectory, DIR), FILE);
        if (!file.exists()) return new ArrayList<>();
        try (Reader reader = new FileReader(file)) {
            HistoryWrapper wrapper = GSON.fromJson(reader, HistoryWrapper.class);
            if (wrapper != null && wrapper.history != null && !wrapper.history.isEmpty()) {
                return new ArrayList<>(wrapper.history);
            }
        } catch (Exception e) {
            LOGGER.warn("[Mercurizer] Could not read benchmark.json — will re-run benchmark: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    private static void writeHistory(List<MercurizerBenchmarkResult> history, File gameDirectory) {
        File dir = new File(gameDirectory, DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            LOGGER.warn("[Mercurizer] Could not create directory: {}", dir);
            return;
        }
        File file = new File(dir, FILE);
        HistoryWrapper wrapper = new HistoryWrapper();
        wrapper.history = history;
        try (Writer writer = new FileWriter(file)) {
            GSON.toJson(wrapper, writer);
        } catch (IOException e) {
            LOGGER.warn("[Mercurizer] Could not save benchmark results: {}", e.getMessage());
        }
    }

    private static MercurizerBenchmarkResult computeWeightedAverage(List<MercurizerBenchmarkResult> history) {
        int n = Math.min(history.size(), MAX_HISTORY);
        double weightSum = 0;
        for (int i = 0; i < n; i++) weightSum += WEIGHTS[i];

        double largeBw = 0, smallBw = 0, cpuMOps = 0, roundTripNs = 0;
        double largeBwWeightSum = 0, smallBwWeightSum = 0, roundTripWeightSum = 0;

        for (int i = 0; i < n; i++) {
            double w = WEIGHTS[i] / weightSum;
            MercurizerBenchmarkResult r = history.get(i);
            cpuMOps += r.cpuThroughputMOpsPerSec * w;
            if (r.bufferUploadBandwidthMBps > 0) {
                largeBw += r.bufferUploadBandwidthMBps * WEIGHTS[i];
                largeBwWeightSum += WEIGHTS[i];
            }
            if (r.smallBufferUploadBandwidthMBps > 0) {
                smallBw += r.smallBufferUploadBandwidthMBps * WEIGHTS[i];
                smallBwWeightSum += WEIGHTS[i];
            }
            if (r.smallBufferRoundTripNs > 0) {
                roundTripNs += r.smallBufferRoundTripNs * WEIGHTS[i];
                roundTripWeightSum += WEIGHTS[i];
            }
        }

        double finalLargeBw   = largeBwWeightSum   > 0 ? largeBw   / largeBwWeightSum   : -1;
        double finalSmallBw   = smallBwWeightSum   > 0 ? smallBw   / smallBwWeightSum   : -1;
        double finalRoundTrip = roundTripWeightSum > 0 ? roundTripNs / roundTripWeightSum : 0;

        MercurizerBenchmarkResult latest = history.get(0);
        return new MercurizerBenchmarkResult(
                finalLargeBw, finalSmallBw,
                cpuMOps, latest.availableProcessors,
                latest.rendererAtBenchmarkTime, latest.driverVersionAtBenchmarkTime,
                latest.benchmarkTimestamp, latest.isRefined,
                finalRoundTrip, latest.benchmarkConfidenceScore, latest.isLowConfidence,
                latest.largeBufferSizeBytes, latest.smallBufferSizeBytes);
    }
}
