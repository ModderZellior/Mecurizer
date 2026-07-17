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
    private static final long WARMUP_LINGER_NS = 60_000_000L;
    private static final long RESULT_LINGER_NS = 3_000_000_000L;

    private static final int LARGE_SIZE_DEFAULT = 4 * 1024 * 1024;
    private static final int SMALL_SIZE_DEFAULT = 1 * 1024 * 1024;

    private static final int LARGE_WARMUP   = 12;
    private static final int LARGE_RUNS     = 30;
    private static final int SMALL_WARMUP   = 12;
    private static final int SMALL_RUNS     = 30;
    private static final int LATENCY_RUNS   = 20;
    private static final int CPU_ARRAY_SIZE = 1024 * 1024;
    private static final int CPU_WARMUP     = 10;
    private static final int CPU_RUNS       = 25;
    private static final int RAMP_UP_MAX    = 20;

    private static final double TRIM        = 0.15;
    private static final double SPIKE_FACTOR = 3.0;
    private static final double COV_LOW_CONFIDENCE = 0.20;

    private static final String BENCH_TITLE = "Mercurizer — Benchmarking GPU & CPU...";
    private static volatile int cachedLargeBufferSize = -1;

    private enum Phase {
        LINGER, INIT, RAMP_UP,
        WARMUP_LARGE, MEASURE_LARGE,
        WARMUP_SMALL, MEASURE_SMALL, MEASURE_LATENCY,
        CPU_WAIT, RESULT
    }

    private final Screen parent;
    private final MercurizerCapabilities caps;
    private Phase phase = Phase.LINGER;
    private long phaseStartNs = -1;
    private int step = 0;

    private int largeBufferSize;
    private int smallBufferSize;

    private int largeVbo = -1, smallVbo = -1, savedBinding = 0;
    private ByteBuffer largeBuf, smallBuf;

    private long[] largeTimeSamples;
    private long[] smallTimeSamples;
    private long[] latencyTimeSamples;

    private double largeBw, smallBw;
    private double largeCov, smallCov;
    private double smallBufferRoundTripNs;

    private final double[] rampBwWindow = new double[3];

    private Thread cpuThread;
    private volatile double cpuMOps = -1;
    private long[] cpuSamples;
    private double cpuCov;

    private MercurizerBenchmarkResult result;
    private long resultSinceNs;

    public MercurizerBenchmarkScreen(Screen parent) {
        super(Component.literal("Mercurizer"));
        this.parent = parent;
        MercurizerCapabilities c = MercurizerCapabilities.getCached();
        this.caps = c != null ? c : MercurizerCapabilities.probeAndCache();
        this.largeBufferSize = detectSodiumLargeBufferSize();
        this.smallBufferSize = largeBufferSize / 4;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (parent != null) {
            parent.extractBackground(graphics, mouseX, mouseY, delta);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        if (phaseStartNs < 0) phaseStartNs = now;

        if (parent != null) {
            parent.extractRenderState(graphics, -999, -999, delta);
        }

        switch (phase) {
            case LINGER:
                draw(graphics, 0f, BENCH_TITLE, "This runs once per GPU or driver update.");
                if (now - phaseStartNs >= WARMUP_LINGER_NS) {
                    advance(caps == null ? Phase.CPU_WAIT : Phase.INIT);
                }
                break;

            case INIT:
                savedBinding = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
                largeBuf = MemoryUtil.memAlloc(largeBufferSize);
                smallBuf = MemoryUtil.memAlloc(smallBufferSize);
                fill(largeBuf); fill(smallBuf);
                largeVbo = GL15.glGenBuffers();
                smallVbo = GL15.glGenBuffers();
                largeTimeSamples   = new long[LARGE_RUNS];
                smallTimeSamples   = new long[SMALL_RUNS];
                latencyTimeSamples = new long[LATENCY_RUNS];
                startCpuThread();
                advance(Phase.RAMP_UP);
                draw(graphics, 0.03f, BENCH_TITLE, "Initialising...");
                break;

            case RAMP_UP: {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, largeVbo);
                largeBuf.rewind();
                long t0 = System.nanoTime();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, largeBuf, GL15.GL_STREAM_DRAW);
                GL11.glFinish();
                double bw = (largeBufferSize * 1e9) / ((System.nanoTime() - t0) * 1024.0 * 1024.0);
                rampBwWindow[step % 3] = bw;
                step++;
                boolean plateau = step >= 3 && isPlateaued(rampBwWindow);
                draw(graphics, 0.03f + Math.min(0.09f, step / (float) RAMP_UP_MAX * 0.09f),
                        BENCH_TITLE, plateau ? "GPU ready." : String.format("GPU power ramp-up (%d/%d)...", step, RAMP_UP_MAX));
                if (plateau || step >= RAMP_UP_MAX) advance(Phase.WARMUP_LARGE);
                break;
            }

            case WARMUP_LARGE:
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, largeVbo);
                largeBuf.rewind();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, largeBuf, GL15.GL_STREAM_DRAW);
                step++;
                draw(graphics, 0.12f + (step / (float) LARGE_WARMUP) * 0.12f,
                        BENCH_TITLE, "Warming up large buffer...");
                if (step >= LARGE_WARMUP) { GL11.glFinish(); advance(Phase.MEASURE_LARGE); }
                break;

            case MEASURE_LARGE: {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, largeVbo);
                largeBuf.rewind();
                long t0 = System.nanoTime();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, largeBuf, GL15.GL_STREAM_DRAW);
                GL11.glFinish();
                largeTimeSamples[step] = System.nanoTime() - t0;
                step++;
                draw(graphics, 0.24f + (step / (float) LARGE_RUNS) * 0.16f,
                        BENCH_TITLE, String.format("Large buffer: sample %d/%d", step, LARGE_RUNS));
                if (step >= LARGE_RUNS) {
                    largeBw  = bandwidthFromSamples(largeTimeSamples, largeBufferSize);
                    largeCov = coefficientOfVariation(largeTimeSamples);
                    advance(Phase.WARMUP_SMALL);
                }
                break;
            }

            case WARMUP_SMALL:
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, smallVbo);
                smallBuf.rewind();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, smallBuf, GL15.GL_STREAM_DRAW);
                step++;
                draw(graphics, 0.40f + (step / (float) SMALL_WARMUP) * 0.10f,
                        BENCH_TITLE, String.format("Large: %.0f MB/s — Warming up small buffer...", largeBw));
                if (step >= SMALL_WARMUP) { GL11.glFinish(); advance(Phase.MEASURE_SMALL); }
                break;

            case MEASURE_SMALL: {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, smallVbo);
                smallBuf.rewind();
                long t0 = System.nanoTime();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, smallBuf, GL15.GL_STREAM_DRAW);
                GL11.glFinish();
                smallTimeSamples[step] = System.nanoTime() - t0;
                step++;
                draw(graphics, 0.50f + (step / (float) SMALL_RUNS) * 0.16f,
                        BENCH_TITLE, String.format("Small buffer: sample %d/%d", step, SMALL_RUNS));
                if (step >= SMALL_RUNS) {
                    smallBw  = bandwidthFromSamples(smallTimeSamples, smallBufferSize);
                    smallCov = coefficientOfVariation(smallTimeSamples);
                    advance(Phase.MEASURE_LATENCY);
                }
                break;
            }

            case MEASURE_LATENCY: {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, smallVbo);
                smallBuf.rewind();
                long t0 = System.nanoTime();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, smallBuf, GL15.GL_STREAM_DRAW);
                GL11.glFinish();
                latencyTimeSamples[step] = System.nanoTime() - t0;
                step++;
                draw(graphics, 0.66f + (step / (float) LATENCY_RUNS) * 0.08f,
                        BENCH_TITLE, String.format("Latency measurement: %d/%d", step, LATENCY_RUNS));
                if (step >= LATENCY_RUNS) {
                    smallBufferRoundTripNs = trimmedMean(removeSpikesByMedian(latencyTimeSamples));
                    MemoryUtil.memFree(largeBuf); MemoryUtil.memFree(smallBuf);
                    GL15.glDeleteBuffers(largeVbo); GL15.glDeleteBuffers(smallVbo);
                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, savedBinding);
                    advance(Phase.CPU_WAIT);
                }
                break;
            }

            case CPU_WAIT:
                if (cpuThread == null) startCpuThread();
                float cpuProg = cpuMOps >= 0 ? 0.95f
                        : Math.min(0.94f, 0.74f + (now - phaseStartNs) / 3_000_000_000f * 0.21f);
                String cpuSubtext = caps != null
                        ? String.format("GPU: %.0f / %.0f MB/s — Running CPU benchmark...", largeBw, smallBw)
                        : "Vulkan backend — Running CPU benchmark...";
                draw(graphics, cpuProg, "Mercurizer — Benchmarking CPU...", cpuSubtext);
                if (cpuMOps >= 0) finalizeBenchmark();
                break;

            case RESULT:
                if (now - resultSinceNs < RESULT_LINGER_NS) {
                    String doneMsg = result.bufferUploadBandwidthMBps < 0
                            ? String.format("Mercurizer — Done. CPU: %.0f MOps/s x %d cores (Vulkan — no GPU benchmark)",
                                    result.cpuThroughputMOpsPerSec, result.availableProcessors)
                            : String.format("Mercurizer — Done. GPU: %.0f MB/s  |  CPU: %.0f MOps/s x %d cores",
                                    result.bufferUploadBandwidthMBps, result.cpuThroughputMOpsPerSec, result.availableProcessors);
                    String confText = result.isLowConfidence
                            ? String.format("Low confidence (CoV %.0f%%) — results may vary", result.benchmarkConfidenceScore * 100)
                            : String.format("Upload: %.1f%%  |  Min budget: %.3f ms  |  Synthetic",
                                    MercurizerTuning.getUploadFraction() * 100,
                                    MercurizerTuning.getMinUploadBudgetNs() / 1_000_000.0);
                    draw(graphics, 1f, doneMsg, confText);
                } else {
                    Minecraft.getInstance().setScreen(parent);
                }
                break;
        }
    }

    private void advance(Phase next) {
        phase = next;
        phaseStartNs = System.nanoTime();
        step = 0;
    }

    private void startCpuThread() {
        cpuThread = new Thread(this::runCpu, "Mercurizer-CPU-Bench");
        cpuThread.setDaemon(true);
        cpuThread.start();
    }

    private void finalizeBenchmark() {
        double worstCov = Math.max(largeCov, Math.max(smallCov, cpuCov));
        boolean lowConf = worstCov > COV_LOW_CONFIDENCE;
        if (lowConf) {
            MERC_LOG.warn("[Mercurizer] Low confidence benchmark result — worst CoV: {}", String.format("%.0f%%", worstCov * 100));
        }

        String renderer = caps != null ? caps.renderer : "Vulkan";
        String version  = caps != null ? caps.version  : "Vulkan";
        double gpuLarge = caps != null ? largeBw              : -1;
        double gpuSmall = caps != null ? smallBw              : -1;
        double rtNs     = caps != null ? smallBufferRoundTripNs : 0;
        int lbSize      = caps != null ? largeBufferSize : 0;
        int sbSize      = caps != null ? smallBufferSize : 0;

        result = new MercurizerBenchmarkResult(
                gpuLarge, gpuSmall,
                cpuMOps, Runtime.getRuntime().availableProcessors(),
                renderer, version,
                System.currentTimeMillis(), false,
                rtNs, worstCov, lowConf,
                lbSize, sbSize);

        MercurizerBenchmarkStore.saveWithHistory(result, Minecraft.getInstance().gameDirectory);
        MercurizerBenchmarkResult weighted = MercurizerBenchmarkStore.load(Minecraft.getInstance().gameDirectory);
        MercurizerTuning.apply(weighted != null ? weighted : result);
        MercurizerTuning.setLatestRaw(result);
        MercurizerBenchmarkController.markDone();
        resultSinceNs = System.nanoTime();
        phase = Phase.RESULT;
    }

    private void runCpu() {
        int[] blocks   = new int[CPU_ARRAY_SIZE];
        int[] vertices = new int[CPU_ARRAY_SIZE];
        for (int i = 0; i < CPU_ARRAY_SIZE; i++) blocks[i] = i * 1664525 + 1013904223;
        for (int w = 0; w < CPU_WARMUP; w++) processCpu(blocks, vertices);

        cpuSamples = new long[CPU_RUNS];
        for (int r = 0; r < CPU_RUNS; r++) {
            long t = System.nanoTime();
            processCpu(blocks, vertices);
            cpuSamples[r] = System.nanoTime() - t;
        }
        double meanNs = trimmedMean(removeSpikesByMedian(cpuSamples));
        cpuCov  = coefficientOfVariation(cpuSamples);
        cpuMOps = meanNs > 0 ? (CPU_ARRAY_SIZE * 1_000.0) / meanNs : 1.0;
    }

    private long processCpu(int[] blocks, int[] vertices) {
        for (int i = 0; i < blocks.length; i++) {
            int b = blocks[i];
            vertices[i] = ((i & 0xF) << 20) | (((i >> 4) & 0xF) << 10) | ((i >> 8) & 0xF)
                    | (((b >>> 4) & 0xFFF) << 1) | Integer.bitCount(b & 0x3F);
            blocks[i] = (b * 1664525 + 1013904223) & 0x7FFFFFFF;
        }
        return blocks.length;
    }

    private double bandwidthFromSamples(long[] samples, int bufferSize) {
        long[] clean = removeSpikesByMedian(samples);
        double meanNs = trimmedMean(clean);
        return meanNs > 0 ? (bufferSize * 1e9) / (meanNs * 1024.0 * 1024.0) : 1.0;
    }

    private long[] removeSpikesByMedian(long[] samples) {
        long[] sorted = Arrays.copyOf(samples, samples.length);
        Arrays.sort(sorted);
        long median = sorted[sorted.length / 2];
        long threshold = (long) (median * SPIKE_FACTOR);
        int valid = 0;
        for (long s : samples) if (s > 0 && s <= threshold) valid++;
        if (valid == 0) return samples;
        long[] out = new long[valid];
        int idx = 0;
        for (long s : samples) if (s > 0 && s <= threshold) out[idx++] = s;
        return out;
    }

    private double trimmedMean(long[] samples) {
        long[] sorted = Arrays.copyOf(samples, samples.length);
        Arrays.sort(sorted);
        int lo = (int) Math.floor(sorted.length * TRIM);
        int hi = sorted.length - lo;
        if (hi <= lo) return sorted[sorted.length / 2];
        double sum = 0;
        for (int i = lo; i < hi; i++) sum += sorted[i];
        return sum / (hi - lo);
    }

    private double coefficientOfVariation(long[] raw) {
        long[] clean  = removeSpikesByMedian(raw);
        long[] sorted = Arrays.copyOf(clean, clean.length);
        Arrays.sort(sorted);
        int lo = (int) Math.floor(sorted.length * TRIM);
        int hi = sorted.length - lo;
        if (hi <= lo) return 0;
        double sum = 0;
        for (int i = lo; i < hi; i++) sum += sorted[i];
        double mean = sum / (hi - lo);
        if (mean <= 0) return 0;
        double varSum = 0;
        for (int i = lo; i < hi; i++) varSum += Math.pow(sorted[i] - mean, 2);
        return Math.sqrt(varSum / (hi - lo)) / mean;
    }

    private boolean isPlateaued(double[] window) {
        double min = Double.MAX_VALUE, max = 0;
        for (double v : window) { if (v > max) max = v; if (v < min) min = v; }
        return min > 0 && (max - min) / min < 0.05;
    }

    private static int detectSodiumLargeBufferSize() {
        if (cachedLargeBufferSize > 0) return cachedLargeBufferSize;
        String[] classes = {
            "net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion",
            "net.caffeinemc.mods.sodium.client.render.chunk.RenderSection",
            "net.caffeinemc.mods.sodium.client.render.chunk.arena.GpuBufferArena"
        };
        String[] fields = {
            "RENDER_PASS_VERTEX_BUFFER_SIZE", "REGION_SIZE", "BUFFER_SIZE",
            "VERTEX_DATA_SIZE", "ARENA_SIZE"
        };
        for (String cls : classes) {
            for (String field : fields) {
                try {
                    Class<?> c = Class.forName(cls);
                    java.lang.reflect.Field f = c.getDeclaredField(field);
                    f.setAccessible(true);
                    Object val = f.get(null);
                    if (val instanceof Integer) {
                        int size = (int) val;
                        if (size >= 512 * 1024 && size <= 32 * 1024 * 1024) {
                            MERC_LOG.info("[Mercurizer] Detected Sodium buffer size: {} KB via {}.{}", size / 1024, cls, field);
                            cachedLargeBufferSize = size;
                            return size;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        MERC_LOG.warn("[Mercurizer] Could not detect Sodium buffer size via reflection — using fallback {} MB", LARGE_SIZE_DEFAULT / (1024 * 1024));
        cachedLargeBufferSize = LARGE_SIZE_DEFAULT;
        return LARGE_SIZE_DEFAULT;
    }

    private void fill(ByteBuffer buf) {
        for (int i = 0; i < buf.capacity(); i++) buf.put((byte) (i & 0xFF));
        buf.flip();
    }

    private void draw(GuiGraphicsExtractor g, float progress, String msg, String sub) {
        g.fill(0, 0, this.width, this.height, 0xBB000000);
        int bw = 320, bh = 8, bx = (this.width - bw) / 2, by = this.height / 2 - 4;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF555555);
        g.fill(bx, by, bx + bw, by + bh, 0xFF222222);
        if (progress > 0f)
            g.fill(bx, by, bx + Math.max(0, Math.min(bw, (int) (bw * progress))), by + bh, 0xFF55FF55);
        g.centeredText(this.font, msg, this.width / 2, by - 16, 0xFFFFFFFF);
        g.centeredText(this.font, sub, this.width / 2, by + bh + 8, 0xFF888888);
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}
