package net.caffeinemc.mods.sodium.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class MercurizerBenchmarkScreen extends Screen {
    private static final Logger MERC_LOG = LoggerFactory.getLogger("Mercurizer");

    // Buffer sizes
    private static final int LARGE_SIZE = 4 * 1024 * 1024;
    private static final int SMALL_SIZE = 1 * 1024 * 1024;

    // Per-phase sample counts
    private static final int RAMP_SAMPLES        = 8;
    private static final int WARMUP_LARGE_FRAMES = 12;
    private static final int MEASURE_LARGE_FRAMES= 30;
    private static final int WARMUP_SMALL_FRAMES = 12;
    private static final int MEASURE_SMALL_FRAMES= 30;
    private static final int LATENCY_FRAMES      = 20;

    // CPU benchmark
    private static final int CPU_ARRAY_SIZE = 1024 * 1024;
    private static final int CPU_WARMUP     = 10;
    private static final int CPU_RUNS       = 25;

    // Stats
    private static final double TRIM         = 0.15;
    private static final double SPIKE_FACTOR = 3.0;
    private static final double COV_LOW_CONF = 0.20;

    // Ramp-up plateau detection
    private static final float PLATEAU_TOL = 0.05f;
    private static final int   PLATEAU_WIN = 3;

    // Linger before starting, linger showing result
    private static final long LINGER_NS = 60_000_000L;
    private static final long RESULT_NS = 3_000_000_000L;

    // Phase identifiers
    private enum Phase {
        LINGER, INIT, RAMP_UP,
        WARMUP_LARGE, MEASURE_LARGE,
        WARMUP_SMALL, MEASURE_SMALL,
        MEASURE_LATENCY, CPU_WAIT, RESULT
    }

    private final Screen parent;
    private Phase phase = Phase.LINGER;
    private long lingerStartNs = -1;

    // GL resources
    private int largeVbo = -1, smallVbo = -1;
    private int savedBinding = 0;
    private ByteBuffer largeBuf, smallBuf;

    // Per-phase counters
    private int frameCounter = 0;

    // Ramp-up
    private final float[] rampBw = new float[RAMP_SAMPLES];
    private int rampIdx = 0;

    // Measurement accumulators
    private final long[] largeSamples  = new long[MEASURE_LARGE_FRAMES];
    private final long[] smallSamples  = new long[MEASURE_SMALL_FRAMES];
    private final long[] latencySamples= new long[LATENCY_FRAMES];
    private int largeIdx = 0, smallIdx = 0, latencyIdx = 0;

    // Intermediate bandwidth (used in WARMUP phases)
    private long phaseFrameStartNs = -1;

    // CPU (runs on background thread)
    private volatile Thread cpuThread = null;
    private volatile double cpuMOps   = 0;
    private volatile double cpuCov    = 0;
    private volatile boolean cpuDone  = false;

    // Final result
    private MercurizerBenchmarkResult result = null;
    private long resultSinceNs = -1;

    // Cached large buffer size for other callers
    static volatile int cachedLargeBufferSize = -1;

    public MercurizerBenchmarkScreen(Screen parent) {
        super(Component.literal("Mercurizer"));
        this.parent = parent;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (parent != null) parent.extractBackground(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (parent != null) parent.extractRenderState(graphics, -999, -999, delta);
        graphics.fill(0, 0, this.width, this.height, 0xBB000000);

        long now = System.nanoTime();

        switch (phase) {
            case LINGER -> {
                if (lingerStartNs < 0) lingerStartNs = now;
                draw(graphics, "Mercurizer — preparing benchmark...", "", 0f);
                if (now - lingerStartNs >= LINGER_NS) phase = Phase.INIT;
            }
            case INIT -> {
                MercurizerCapabilities caps = MercurizerCapabilities.probeAndCache();
                if (caps == null) {
                    // Vulkan — CPU only
                    startCpuThread();
                    phase = Phase.CPU_WAIT;
                    break;
                }
                savedBinding = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
                largeBuf = MemoryUtil.memAlloc(LARGE_SIZE);
                smallBuf = MemoryUtil.memAlloc(SMALL_SIZE);
                fillBuf(largeBuf); fillBuf(smallBuf);
                largeVbo = GL15.glGenBuffers();
                smallVbo = GL15.glGenBuffers();
                cachedLargeBufferSize = LARGE_SIZE;
                startCpuThread();
                phase = Phase.RAMP_UP;
                frameCounter = 0; rampIdx = 0;
                draw(graphics, "Mercurizer — warming up GPU...", "Phase 1 of 4", 0.05f);
            }
            case RAMP_UP -> {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, largeVbo);
                largeBuf.rewind();
                long t0 = System.nanoTime();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, largeBuf, GL15.GL_STREAM_DRAW);
                GL11.glFinish();
                long dt = System.nanoTime() - t0;
                float bw = dt > 0 ? (float)((LARGE_SIZE * 1e9) / (dt * 1024.0 * 1024.0)) : 1f;
                rampBw[rampIdx % RAMP_SAMPLES] = bw;
                rampIdx++;
                frameCounter++;
                draw(graphics, "Mercurizer — warming up GPU...", "Phase 1 of 4", 0.05f + 0.10f * (frameCounter / (float)RAMP_SAMPLES));
                if (rampIdx >= RAMP_SAMPLES && isPlateaued()) {
                    phase = Phase.WARMUP_LARGE;
                    frameCounter = 0;
                }
            }
            case WARMUP_LARGE -> {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, largeVbo);
                largeBuf.rewind();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, largeBuf, GL15.GL_STREAM_DRAW);
                GL11.glFinish();
                frameCounter++;
                draw(graphics, "Mercurizer — measuring large buffer...", "Phase 2 of 4",
                        0.15f + 0.20f * (frameCounter / (float)WARMUP_LARGE_FRAMES));
                if (frameCounter >= WARMUP_LARGE_FRAMES) { phase = Phase.MEASURE_LARGE; frameCounter = 0; largeIdx = 0; }
            }
            case MEASURE_LARGE -> {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, largeVbo);
                largeBuf.rewind();
                long t0 = System.nanoTime();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, largeBuf, GL15.GL_STREAM_DRAW);
                GL11.glFinish();
                largeSamples[largeIdx++] = System.nanoTime() - t0;
                frameCounter++;
                draw(graphics, "Mercurizer — measuring large buffer...", "Phase 2 of 4",
                        0.35f + 0.15f * (frameCounter / (float)MEASURE_LARGE_FRAMES));
                if (largeIdx >= MEASURE_LARGE_FRAMES) { phase = Phase.WARMUP_SMALL; frameCounter = 0; }
            }
            case WARMUP_SMALL -> {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, smallVbo);
                smallBuf.rewind();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, smallBuf, GL15.GL_STREAM_DRAW);
                GL11.glFinish();
                frameCounter++;
                draw(graphics, "Mercurizer — measuring small buffer...", "Phase 3 of 4",
                        0.50f + 0.10f * (frameCounter / (float)WARMUP_SMALL_FRAMES));
                if (frameCounter >= WARMUP_SMALL_FRAMES) { phase = Phase.MEASURE_SMALL; frameCounter = 0; smallIdx = 0; }
            }
            case MEASURE_SMALL -> {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, smallVbo);
                smallBuf.rewind();
                long t0 = System.nanoTime();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, smallBuf, GL15.GL_STREAM_DRAW);
                GL11.glFinish();
                smallSamples[smallIdx++] = System.nanoTime() - t0;
                frameCounter++;
                draw(graphics, "Mercurizer — measuring small buffer...", "Phase 3 of 4",
                        0.60f + 0.10f * (frameCounter / (float)MEASURE_SMALL_FRAMES));
                if (smallIdx >= MEASURE_SMALL_FRAMES) { phase = Phase.MEASURE_LATENCY; frameCounter = 0; latencyIdx = 0; }
            }
            case MEASURE_LATENCY -> {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, smallVbo);
                smallBuf.rewind();
                long t0 = System.nanoTime();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, smallBuf, GL15.GL_STREAM_DRAW);
                GL11.glFinish();
                latencySamples[latencyIdx++] = System.nanoTime() - t0;
                frameCounter++;
                draw(graphics, "Mercurizer — measuring latency...", "Phase 4 of 4",
                        0.70f + 0.10f * (frameCounter / (float)LATENCY_FRAMES));
                if (latencyIdx >= LATENCY_FRAMES) { phase = Phase.CPU_WAIT; frameCounter = 0; freeGlResources(); }
            }
            case CPU_WAIT -> {
                draw(graphics, "Mercurizer — waiting for CPU benchmark...", "Phase 4 of 4", 0.85f);
                if (cpuDone) finalizeBenchmark();
            }
            case RESULT -> {
                if (result == null) { Minecraft.getInstance().setScreenAndShow(parent); break; }
                String line1 = result.bufferUploadBandwidthMBps < 0
                        ? String.format("Done.  CPU: %.0f MOps/s x %d cores", result.cpuThroughputMOpsPerSec, result.availableProcessors)
                        : String.format("Done.  GPU: %.0f / %.0f MB/s  |  CPU: %.0f MOps/s x %d",
                                result.bufferUploadBandwidthMBps, result.smallBufferUploadBandwidthMBps,
                                result.cpuThroughputMOpsPerSec, result.availableProcessors);
                String line2 = String.format("Upload fraction: %.0f%%  |  Budget: %.2f ms%s",
                        MercurizerFrameTracker.getUploadFraction() * 100,
                        MercurizerFrameTracker.getUploadBudgetNs() / 1_000_000.0,
                        result.isLowConfidence ? "  [LOW CONFIDENCE]" : "");
                draw(graphics, line1, line2, 1f);
                if (now - resultSinceNs >= RESULT_NS) Minecraft.getInstance().setScreenAndShow(parent);
            }
        }
    }

    private void startCpuThread() {
        cpuThread = new Thread(() -> {
            int[] blocks   = new int[CPU_ARRAY_SIZE];
            int[] vertices = new int[CPU_ARRAY_SIZE];
            for (int i = 0; i < CPU_ARRAY_SIZE; i++) blocks[i] = i * 1664525 + 1013904223;
            for (int w = 0; w < CPU_WARMUP; w++) processCpu(blocks, vertices);
            long[] samples = new long[CPU_RUNS];
            for (int r = 0; r < CPU_RUNS; r++) {
                long t = System.nanoTime(); processCpu(blocks, vertices); samples[r] = System.nanoTime() - t;
            }
            double mean = trimmedMean(removeSpikesByMedian(samples));
            cpuMOps = mean > 0 ? (CPU_ARRAY_SIZE * 1_000.0) / mean : 1.0;
            cpuCov  = coefficientOfVariation(samples);
            cpuDone = true;
        }, "mercurizer-cpu-bench");
        cpuThread.setDaemon(true);
        cpuThread.start();
    }

    private void finalizeBenchmark() {
        MercurizerCapabilities caps = MercurizerCapabilities.getCached();

        double largeBw = -1, smallBw = -1, roundTripNs = 0, largeCov = 0, smallCov = 0;
        if (caps != null) {
            largeBw    = bwFromSamples(largeSamples, LARGE_SIZE);
            smallBw    = bwFromSamples(smallSamples, SMALL_SIZE);
            roundTripNs= trimmedMean(removeSpikesByMedian(latencySamples));
            largeCov   = coefficientOfVariation(largeSamples);
            smallCov   = coefficientOfVariation(smallSamples);
        }
        double worstCov = Math.max(largeCov, Math.max(smallCov, cpuCov));
        boolean lowConf = worstCov > COV_LOW_CONF;
        int cores = Runtime.getRuntime().availableProcessors();
        String renderer = caps != null ? caps.renderer : "Vulkan";
        String driver   = caps != null ? caps.version  : "Vulkan";

        result = new MercurizerBenchmarkResult(
                largeBw, smallBw, cpuMOps, cores,
                renderer, driver,
                System.currentTimeMillis(), false,
                roundTripNs, worstCov, lowConf,
                LARGE_SIZE, SMALL_SIZE);

        MercurizerBenchmarkController.onBenchmarkComplete(result);
        resultSinceNs = System.nanoTime();
        phase = Phase.RESULT;

        MERC_LOG.info("[Mercurizer] Benchmark done — GPU: {}/{} MB/s  CPU: {} MOps/s x {}  RT: {} us  CoV: {}{}",
                String.format("%.0f", largeBw), String.format("%.0f", smallBw),
                String.format("%.0f", cpuMOps), cores,
                String.format("%.0f", roundTripNs / 1000.0),
                String.format("%.0f%%", worstCov * 100),
                lowConf ? " [LOW CONFIDENCE]" : "");
    }

    private void freeGlResources() {
        if (largeBuf != null) { MemoryUtil.memFree(largeBuf); largeBuf = null; }
        if (smallBuf != null) { MemoryUtil.memFree(smallBuf); smallBuf = null; }
        if (largeVbo >= 0) { GL15.glDeleteBuffers(largeVbo); largeVbo = -1; }
        if (smallVbo >= 0) { GL15.glDeleteBuffers(smallVbo); smallVbo = -1; }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, savedBinding);
    }

    @Override
    public void onClose() { /* block ESC — benchmark must finish */ }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    private boolean isPlateaued() {
        if (rampIdx < RAMP_SAMPLES) return false;
        float[] last = new float[PLATEAU_WIN];
        for (int i = 0; i < PLATEAU_WIN; i++) last[i] = rampBw[(rampIdx - PLATEAU_WIN + i) % RAMP_SAMPLES];
        float avg = 0; for (float v : last) avg += v; avg /= PLATEAU_WIN;
        for (float v : last) if (Math.abs(v - avg) / avg > PLATEAU_TOL) return false;
        return true;
    }

    private void draw(GuiGraphicsExtractor graphics, String msg, String sub, float progress) {
        int barW = 320, barH = 8;
        int barX = (this.width - barW) / 2;
        int barY = this.height / 2 - 4;
        graphics.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xFF555555);
        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF222222);
        if (progress > 0f) {
            int fill = Math.max(0, Math.min(barW, (int)(barW * progress)));
            graphics.fill(barX, barY, barX + fill, barY + barH, 0xFF55FF55);
        }
        graphics.centeredText(this.font, msg, this.width / 2, barY - 16, 0xFFFFFFFF);
        if (!sub.isEmpty()) graphics.centeredText(this.font, sub, this.width / 2, barY + barH + 8, 0xFF888888);
    }

    // --- Statistics helpers ---

    private static long[] removeSpikesByMedian(long[] s) {
        long[] sorted = Arrays.copyOf(s, s.length); Arrays.sort(sorted);
        long median = sorted[sorted.length / 2];
        long threshold = (long)(median * SPIKE_FACTOR);
        int valid = 0; for (long v : s) if (v > 0 && v <= threshold) valid++;
        if (valid == 0) return s;
        long[] out = new long[valid]; int idx = 0;
        for (long v : s) if (v > 0 && v <= threshold) out[idx++] = v;
        return out;
    }

    private static double trimmedMean(long[] s) {
        long[] sorted = Arrays.copyOf(s, s.length); Arrays.sort(sorted);
        int lo = (int)Math.floor(sorted.length * TRIM);
        int hi = sorted.length - lo;
        if (hi <= lo) return sorted[sorted.length / 2];
        double sum = 0; for (int i = lo; i < hi; i++) sum += sorted[i];
        return sum / (hi - lo);
    }

    private static double coefficientOfVariation(long[] raw) {
        long[] clean = removeSpikesByMedian(raw);
        long[] sorted = Arrays.copyOf(clean, clean.length); Arrays.sort(sorted);
        int lo = (int)Math.floor(sorted.length * TRIM);
        int hi = sorted.length - lo;
        if (hi <= lo) return 0;
        double sum = 0; for (int i = lo; i < hi; i++) sum += sorted[i];
        double mean = sum / (hi - lo); if (mean <= 0) return 0;
        double varSum = 0; for (int i = lo; i < hi; i++) varSum += Math.pow(sorted[i] - mean, 2);
        return Math.sqrt(varSum / (hi - lo)) / mean;
    }

    private static double bwFromSamples(long[] s, int size) {
        double mean = trimmedMean(removeSpikesByMedian(s));
        return mean > 0 ? (size * 1e9) / (mean * 1024.0 * 1024.0) : 1.0;
    }

    private static void processCpu(int[] blocks, int[] vertices) {
        for (int i = 0; i < blocks.length; i++) {
            int b = blocks[i];
            int state = (b >>> 4) & 0xFFF;
            int vis   = Integer.bitCount(b & 0x3F);
            int x = i & 0xF, y = (i >> 4) & 0xF, z = (i >> 8) & 0xF;
            vertices[i] = (x << 20) | (y << 10) | z | (state << 1) | vis;
            blocks[i]   = (b * 1664525 + 1013904223) & 0x7FFFFFFF;
        }
    }

    private static void fillBuf(ByteBuffer buf) {
        for (int i = 0; i < buf.capacity(); i++) buf.put((byte)(i & 0xFF));
        buf.flip();
    }
}
