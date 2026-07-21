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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MercurizerBenchmarkScreen extends Screen {
    private static final Logger MERC_LOG = LoggerFactory.getLogger("Mercurizer");

    private static final int    CHUNK_SIZE    = 128 * 1024;
    private static final int    WARMUP_FRAMES = 8;
    private static final int    MIN_SAMPLES   = 10;
    private static final int    MAX_SAMPLES   = 60;
    private static final double TARGET_COV    = 0.08;
    private static final int    RAMP_SAMPLES  = 12;
    private static final int    LATENCY_RUNS  = 20;
    private static final int    CPU_ARRAY_SIZE = 1024 * 1024;
    private static final int    CPU_WARMUP    = 10;
    private static final int    CPU_RUNS      = 25;
    private static final double TRIM          = 0.15;
    private static final double SPIKE_FACTOR  = 3.0;
    private static final double COV_LOW_CONF  = 0.20;
    private static final long   LINGER_NS     = 60_000_000L;
    private static final long   RESULT_NS     = 3_000_000_000L;

    private static final float PLATEAU_TOL = 0.05f;
    private static final int   PLATEAU_WIN = 3;

    private enum Phase {
        LINGER, INIT, RAMP_UP,
        WARMUP_SIZE, MEASURE_SIZE,
        MEASURE_LATENCY, CPU_WAIT, RESULT
    }

    private final Screen parent;
    private Phase phase = Phase.LINGER;
    private long lingerStartNs = -1;

    // Multi-size
    private int[]        sizeSet;
    private int          sizeIndex;
    private int[]        allVbos;
    private ByteBuffer[] allBufs;
    private int          savedBinding;
    private double[]     bwResults;
    private double[]     covResults;

    // Adaptive sampling
    private final List<Long> currentSamples = new ArrayList<>();
    private int frameCounter;

    // Ramp-up
    private final float[] rampBw  = new float[RAMP_SAMPLES];
    private int           rampIdx;

    // Latency
    private final long[] latencySamples = new long[LATENCY_RUNS];
    private int          latencyIdx;

    // CPU benchmark
    private volatile Thread  cpuThread = null;
    private volatile double  cpuMOps   = 0;
    private volatile double  cpuCov    = 0;
    private volatile boolean cpuDone   = false;

    // Result + re-run
    private MercurizerBenchmarkResult result = null;
    private long resultSinceNs = -1;
    private int  attemptNumber = 1;
    private MercurizerBenchmarkResult bestAttempt = null;

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
        if (parent != null) parent.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);

        long now = System.nanoTime();

        switch (phase) {
            case LINGER -> {
                if (lingerStartNs < 0) lingerStartNs = now;
                int startRun = MercurizerBenchmarkController.startupRunNumber;
                String lingerSub = startRun >= 2
                        ? String.format("Running again to improve accuracy (run %d of 3)", startRun)
                        : "";
                draw(graphics, "Mercurizer  -  preparing benchmark...", lingerSub, 0f);
                if (now - lingerStartNs >= LINGER_NS) phase = Phase.INIT;
            }
            case INIT -> {
                MercurizerCapabilities caps = MercurizerCapabilities.probeAndCache();
                if (caps == null) {
                    startCpuThread();
                    phase = Phase.CPU_WAIT;
                    break;
                }
                savedBinding = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
                int largeSize = detectLargeBufferSize();
                cachedLargeBufferSize = largeSize;
                buildSizeSet(caps, largeSize);

                allVbos = new int[sizeSet.length];
                allBufs = new ByteBuffer[sizeSet.length];
                for (int i = 0; i < sizeSet.length; i++) {
                    allBufs[i] = MemoryUtil.memAlloc(sizeSet[i]);
                    fillBuf(allBufs[i]);
                    allVbos[i] = GL15.glGenBuffers();
                }
                bwResults  = new double[sizeSet.length];
                covResults = new double[sizeSet.length];
                sizeIndex  = 0;
                startCpuThread();
                phase = Phase.RAMP_UP;
                frameCounter = 0; rampIdx = 0;
                draw(graphics, "Mercurizer  -  warming up GPU...", "Phase 1 of " + (sizeSet.length + 2), 0.05f);
            }
            case RAMP_UP -> {
                int lastIdx = allVbos.length - 1;
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, allVbos[lastIdx]);
                allBufs[lastIdx].rewind();
                long t0 = System.nanoTime();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, allBufs[lastIdx], GL15.GL_STREAM_DRAW);
                GL11.glFinish();
                long dt = System.nanoTime() - t0;
                float bw = dt > 0 ? (float)((sizeSet[lastIdx] * 1e9) / (dt * 1024.0 * 1024.0)) : 1f;
                rampBw[rampIdx % RAMP_SAMPLES] = bw;
                rampIdx++;
                frameCounter++;
                draw(graphics, "Mercurizer  -  warming up GPU...",
                        "Phase 1 of " + (sizeSet.length + 2),
                        0.05f + 0.10f * Math.min(1f, frameCounter / (float)RAMP_SAMPLES));
                boolean plateaued = rampIdx >= RAMP_SAMPLES && isPlateaued();
                boolean timedOut  = rampIdx >= RAMP_SAMPLES * 3;
                if (plateaued || timedOut) {
                    phase = Phase.WARMUP_SIZE;
                    frameCounter = 0;
                    currentSamples.clear();
                }
            }
            case WARMUP_SIZE -> {
                int si = sizeIndex;
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, allVbos[si]);
                allBufs[si].rewind();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, allBufs[si], GL15.GL_STREAM_DRAW);
                GL11.glFinish();
                frameCounter++;
                int phaseNum = 2 + si;
                int totalPhases = sizeSet.length + 2;
                draw(graphics, String.format("Mercurizer  -  warming up %s...", sizeLabel(sizeSet[si])),
                        "Phase " + phaseNum + " of " + totalPhases,
                        sizeProgress(si, frameCounter / (float)WARMUP_FRAMES));
                if (frameCounter >= WARMUP_FRAMES) {
                    phase = Phase.MEASURE_SIZE;
                    frameCounter = 0;
                    currentSamples.clear();
                }
            }
            case MEASURE_SIZE -> {
                int si = sizeIndex;
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, allVbos[si]);
                allBufs[si].rewind();
                long ns = measureUpload(allBufs[si]);
                currentSamples.add(ns);
                frameCounter++;
                double cov = currentCov();
                boolean enoughSamples = currentSamples.size() >= MIN_SAMPLES;
                boolean covOk   = enoughSamples && cov <= TARGET_COV;
                boolean maxHit  = currentSamples.size() >= MAX_SAMPLES;
                draw(graphics,
                        String.format("Mercurizer  -  measuring %s...", sizeLabel(sizeSet[si])),
                        String.format("%s: %d samples, CoV %.0f%%%s",
                                sizeLabel(sizeSet[si]), currentSamples.size(), cov * 100, ""),
                        sizeProgress(si, Math.min(1f, currentSamples.size() / (float)MAX_SAMPLES)));
                if (covOk || maxHit) {
                    long[] arr = toLongArray(currentSamples);
                    bwResults[si]  = bwFromNs(arr, sizeSet[si]);
                    covResults[si] = coefficientOfVariation(arr);
                    sizeIndex++;
                    if (sizeIndex < sizeSet.length) {
                        phase = Phase.WARMUP_SIZE;
                        frameCounter = 0;
                        currentSamples.clear();
                    } else {
                        phase = Phase.MEASURE_LATENCY;
                        frameCounter = 0;
                        latencyIdx = 0;
                    }
                }
            }
            case MEASURE_LATENCY -> {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, allVbos[0]);
                allBufs[0].rewind();
                latencySamples[latencyIdx++] = measureUpload(allBufs[0]);
                frameCounter++;
                int totalPhases = sizeSet != null ? sizeSet.length + 2 : 4;
                draw(graphics, "Mercurizer  -  measuring latency...",
                        "Phase " + totalPhases + " of " + totalPhases,
                        0.85f + 0.05f * (frameCounter / (float)LATENCY_RUNS));
                if (latencyIdx >= LATENCY_RUNS) {
                    freeGlResources();
                }
            }
            case CPU_WAIT -> {
                String cpuSub = attemptNumber > 1
                        ? String.format("High variance  -  re-running (attempt %d of 3)...", attemptNumber)
                        : "Waiting for CPU benchmark...";
                draw(graphics, "Mercurizer  -  waiting for CPU benchmark...", cpuSub, 0.90f);
                if (cpuDone) handleBenchmarkDone();
            }
            case RESULT -> {
                if (result == null) { Minecraft.getInstance().setScreen(parent); break; }
                String line1 = result.bufferUploadBandwidthMBps < 0
                        ? String.format("Done.  CPU: %.0f MOps/s x %d cores",
                                result.cpuThroughputMOpsPerSec, result.availableProcessors)
                        : String.format("Done.  Chunk: %.0f MB/s  |  Region: %.0f MB/s  |  CPU: %.0f MOps/s x %d",
                                result.smallBufferUploadBandwidthMBps, result.bufferUploadBandwidthMBps,
                                result.cpuThroughputMOpsPerSec, result.availableProcessors);
                String line2 = String.format("Upload: %.1f%%  |  Min budget: %.3f ms%s",
                        MercurizerFrameTracker.getUploadFraction() * 100,
                        MercurizerFrameTracker.getUploadBudgetNs() / 1_000_000.0,
                        result.isLowConfidence ? "  [LOW CONFIDENCE]" : "");
                draw(graphics, line1, line2, 1f);
                if (now - resultSinceNs >= RESULT_NS) Minecraft.getInstance().setScreen(parent);
            }
        }
    }

    // --- GL helpers ---

    private long measureUpload(ByteBuffer buf) {
        long t0 = System.nanoTime();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STREAM_DRAW);
        GL11.glFinish();
        return System.nanoTime() - t0;
    }

    private void freeGlResources() {
        if (allBufs != null) {
            for (ByteBuffer b : allBufs) if (b != null) MemoryUtil.memFree(b);
            allBufs = null;
        }
        if (allVbos != null) {
            for (int v : allVbos) if (v >= 0) GL15.glDeleteBuffers(v);
            allVbos = null;
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, savedBinding);
        phase = Phase.CPU_WAIT;
    }

    // --- Size set ---

    private void buildSizeSet(MercurizerCapabilities caps, int largeSize) {
        if (caps == null) { sizeSet = new int[]{ CHUNK_SIZE }; return; }
        int[] candidates = { CHUNK_SIZE, 512 * 1024, 2 * 1024 * 1024, largeSize };
        Arrays.sort(candidates);
        int n = 1;
        for (int i = 1; i < candidates.length; i++)
            if (candidates[i] > candidates[n - 1] + 32 * 1024) candidates[n++] = candidates[i];
        sizeSet = Arrays.copyOf(candidates, n);
    }

    private static int detectLargeBufferSize() {
        String[] classNames = {
            "net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion",
            "net.caffeinemc.mods.sodium.client.render.chunk.RenderSection",
            "net.caffeinemc.mods.sodium.client.render.chunk.arena.GpuBufferArena"
        };
        String[] fieldNames = {
            "RENDER_PASS_VERTEX_BUFFER_SIZE", "REGION_SIZE", "BUFFER_SIZE",
            "VERTEX_DATA_SIZE", "ARENA_SIZE"
        };
        for (String className : classNames) {
            for (String field : fieldNames) {
                try {
                    Class<?> cls = Class.forName(className);
                    java.lang.reflect.Field f = cls.getDeclaredField(field);
                    f.setAccessible(true);
                    Object val = f.get(null);
                    if (val instanceof Integer) {
                        int size = (Integer) val;
                        if (size >= 512 * 1024 && size <= 32 * 1024 * 1024) return size;
                    }
                } catch (Exception ignored) {}
            }
        }
        return 4 * 1024 * 1024;
    }

    private static String sizeLabel(int size) {
        if (size >= 1024 * 1024) return String.format("%.0f MB", size / (1024.0 * 1024.0));
        return String.format("%d KB", size / 1024);
    }

    private float sizeProgress(int completedSizes, float sub) {
        int total = sizeSet != null ? sizeSet.length : 3;
        float perSize = 0.75f / (total + 1);
        return 0.15f + completedSizes * perSize + sub * perSize;
    }

    // --- CPU thread ---

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

    // --- Benchmark completion ---

    private void handleBenchmarkDone() {
        MercurizerBenchmarkResult candidate = computeCandidate();
        if (candidate.isLowConfidence && attemptNumber < 3) {
            if (bestAttempt == null || candidate.benchmarkConfidenceScore < bestAttempt.benchmarkConfidenceScore) {
                bestAttempt = candidate;
            }
            attemptNumber++;
            MERC_LOG.warn("[Mercurizer] High variance (CoV {})  -  re-running attempt {} of 3",
                    String.format("%.0f%%", candidate.benchmarkConfidenceScore * 100), attemptNumber);
            resetForRerun();
        } else {
            MercurizerBenchmarkResult finalResult = (bestAttempt != null
                    && bestAttempt.benchmarkConfidenceScore < candidate.benchmarkConfidenceScore)
                    ? bestAttempt : candidate;
            acceptResult(finalResult);
        }
    }

    private MercurizerBenchmarkResult computeCandidate() {
        MercurizerCapabilities caps = MercurizerCapabilities.getCached();
        double largeBw = -1, chunkBw = -1, roundTripNs = 0;
        double worstCov = cpuCov;
        if (caps != null && bwResults != null) {
            largeBw     = bwResults[bwResults.length - 1];
            chunkBw     = bwResults[0];
            roundTripNs = trimmedMean(removeSpikesByMedian(latencySamples));
            for (double c : covResults) worstCov = Math.max(worstCov, c);
        }
        boolean lowConf = worstCov > COV_LOW_CONF;
        int lbSize  = sizeSet != null ? sizeSet[sizeSet.length - 1] : 0;
        int sbSize  = sizeSet != null ? sizeSet[0] : 0;
        String renderer = caps != null ? caps.renderer : "Vulkan";
        String driver   = caps != null ? caps.version  : "Vulkan";
        return new MercurizerBenchmarkResult(largeBw, chunkBw, cpuMOps,
                Runtime.getRuntime().availableProcessors(),
                renderer, driver, System.currentTimeMillis(), false,
                roundTripNs, worstCov, lowConf, lbSize, sbSize);
    }

    private void acceptResult(MercurizerBenchmarkResult r) {
        result = r;
        MercurizerBenchmarkController.onBenchmarkComplete(r);
        resultSinceNs = System.nanoTime();
        phase = Phase.RESULT;
        MERC_LOG.info("[Mercurizer] Benchmark done (attempt {})  -  Chunk: {} MB/s  Region: {} MB/s  CPU: {} MOps/s x {}  CoV: {}{}",
                attemptNumber,
                String.format("%.0f", r.smallBufferUploadBandwidthMBps),
                String.format("%.0f", r.bufferUploadBandwidthMBps),
                String.format("%.0f", r.cpuThroughputMOpsPerSec),
                r.availableProcessors,
                String.format("%.0f%%", r.benchmarkConfidenceScore * 100),
                r.isLowConfidence ? " [LOW CONFIDENCE]" : "");
    }

    private void resetForRerun() {
        MercurizerCapabilities caps = MercurizerCapabilities.getCached();
        if (caps != null) {
            savedBinding = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);            allVbos = new int[sizeSet.length];
            allBufs = new ByteBuffer[sizeSet.length];
            for (int i = 0; i < sizeSet.length; i++) {
                allBufs[i] = MemoryUtil.memAlloc(sizeSet[i]);
                fillBuf(allBufs[i]);
                allVbos[i] = GL15.glGenBuffers();
            }
            bwResults  = new double[sizeSet.length];
            covResults = new double[sizeSet.length];
        }
        sizeIndex = 0;
        currentSamples.clear();
        frameCounter = 0;
        rampIdx      = 0;
        latencyIdx   = 0;
        cpuDone = false; cpuMOps = 0; cpuCov = 0;
        cpuThread = null;
        startCpuThread();
        phase = caps != null ? Phase.RAMP_UP : Phase.CPU_WAIT;
    }

    @Override
    public void onClose() { /* block ESC  -  benchmark must finish */ }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    // --- Plateau detection ---

    private boolean isPlateaued() {
        if (rampIdx < RAMP_SAMPLES) return false;
        float[] last = new float[PLATEAU_WIN];
        for (int i = 0; i < PLATEAU_WIN; i++) last[i] = rampBw[(rampIdx - PLATEAU_WIN + i) % RAMP_SAMPLES];
        float avg = 0; for (float v : last) avg += v; avg /= PLATEAU_WIN;
        for (float v : last) if (Math.abs(v - avg) / avg > PLATEAU_TOL) return false;
        return true;
    }

    // --- Draw ---

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
        if (sub != null && !sub.isEmpty())
            graphics.centeredText(this.font, sub, this.width / 2, barY + barH + 8, 0xFF888888);
    }

    // --- CoV helpers ---

    private double currentCov() {
        if (currentSamples.size() < 2) return 1.0;
        return coefficientOfVariation(toLongArray(currentSamples));
    }

    private static long[] toLongArray(List<Long> list) {
        long[] a = new long[list.size()];
        for (int i = 0; i < a.length; i++) a[i] = list.get(i);
        return a;
    }

    // --- Statistics helpers ---

    private static double bwFromNs(long[] samples, int size) {
        double mean = trimmedMean(removeSpikesByMedian(samples));
        return mean > 0 ? (size * 1e9) / (mean * 1024.0 * 1024.0) : 1.0;
    }

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

    private static void processCpu(int[] blocks, int[] vertices) {
        for (int i = 0; i < blocks.length; i++) {
            int b     = blocks[i];
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
