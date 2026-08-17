package net.caffeinemc.mods.sodium.client;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class MercurizerBenchmark {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mercurizer");

    private static final int LARGE_SIZE = 4 * 1024 * 1024;
    private static final int SMALL_SIZE = 1024 * 1024;
    private static final int LARGE_WARMUP = 12;
    private static final int LARGE_RUNS = 30;
    private static final int SMALL_WARMUP = 12;
    private static final int SMALL_RUNS = 30;
    private static final int LATENCY_RUNS = 20;
    private static final int CPU_ARRAY_SIZE = 1024 * 1024;
    private static final int CPU_WARMUP = 10;
    private static final int CPU_RUNS = 25;
    private static final double TRIM = 0.15;
    private static final double SPIKE_FACTOR = 3.0;
    private static final double COV_LOW_CONFIDENCE = 0.20;

    public static MercurizerBenchmarkResult run(MercurizerCapabilities caps) {
        if (caps == null) return runCpuOnly();
        return runFull(caps);
    }

    private static MercurizerBenchmarkResult runCpuOnly() {
        LOGGER.info("[Mercurizer] Vulkan backend — running CPU benchmark only");
        double[] cpuResult = runCpuBenchmark();
        double cpuMOps = cpuResult[0];
        double cpuCov  = cpuResult[1];
        int cores = Runtime.getRuntime().availableProcessors();
        boolean lowConf = cpuCov > COV_LOW_CONFIDENCE;
        LOGGER.info("[Mercurizer]   CPU: {} MOps/s x {} cores (CoV {}){}",
                String.format("%.0f", cpuMOps), cores,
                String.format("%.0f%%", cpuCov * 100),
                lowConf ? " [LOW CONFIDENCE]" : "");
        return new MercurizerBenchmarkResult(
                -1, -1, cpuMOps, cores,
                "Vulkan", "Vulkan",
                System.currentTimeMillis(), false,
                0, cpuCov, lowConf, 0, 0);
    }

    private static MercurizerBenchmarkResult runFull(MercurizerCapabilities caps) {
        LOGGER.info("[Mercurizer] Starting GPU + CPU benchmark...");
        int savedBinding = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        ByteBuffer largeBuf = MemoryUtil.memAlloc(LARGE_SIZE);
        ByteBuffer smallBuf = MemoryUtil.memAlloc(SMALL_SIZE);
        fillBuffer(largeBuf); fillBuffer(smallBuf);
        int largeVbo = GL15.glGenBuffers();
        int smallVbo = GL15.glGenBuffers();

        double largeBw, smallBw, largeCov, smallCov, roundTripNs;
        try {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, largeVbo);
            for (int i = 0; i < LARGE_WARMUP; i++) { largeBuf.rewind(); GL15.glBufferData(GL15.GL_ARRAY_BUFFER, largeBuf, GL15.GL_STREAM_DRAW); }
            GL11.glFinish();
            long[] largeSamples = new long[LARGE_RUNS];
            for (int i = 0; i < LARGE_RUNS; i++) {
                largeBuf.rewind();
                long t = System.nanoTime();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, largeBuf, GL15.GL_STREAM_DRAW);
                GL11.glFinish();
                largeSamples[i] = System.nanoTime() - t;
            }
            largeBw  = bandwidthFromSamples(largeSamples, LARGE_SIZE);
            largeCov = coefficientOfVariation(largeSamples);

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, smallVbo);
            for (int i = 0; i < SMALL_WARMUP; i++) { smallBuf.rewind(); GL15.glBufferData(GL15.GL_ARRAY_BUFFER, smallBuf, GL15.GL_STREAM_DRAW); }
            GL11.glFinish();
            long[] smallSamples = new long[SMALL_RUNS];
            for (int i = 0; i < SMALL_RUNS; i++) {
                smallBuf.rewind();
                long t = System.nanoTime();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, smallBuf, GL15.GL_STREAM_DRAW);
                GL11.glFinish();
                smallSamples[i] = System.nanoTime() - t;
            }
            smallBw  = bandwidthFromSamples(smallSamples, SMALL_SIZE);
            smallCov = coefficientOfVariation(smallSamples);

            long[] latencySamples = new long[LATENCY_RUNS];
            for (int i = 0; i < LATENCY_RUNS; i++) {
                smallBuf.rewind();
                long t = System.nanoTime();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, smallBuf, GL15.GL_STREAM_DRAW);
                GL11.glFinish();
                latencySamples[i] = System.nanoTime() - t;
            }
            roundTripNs = trimmedMean(removeSpikesByMedian(latencySamples));
        } finally {
            MemoryUtil.memFree(largeBuf); MemoryUtil.memFree(smallBuf);
            GL15.glDeleteBuffers(largeVbo); GL15.glDeleteBuffers(smallVbo);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, savedBinding);
        }

        double[] cpuResult = runCpuBenchmark();
        double cpuMOps = cpuResult[0];
        double cpuCov  = cpuResult[1];
        int cores = Runtime.getRuntime().availableProcessors();
        double worstCov = Math.max(largeCov, Math.max(smallCov, cpuCov));
        boolean lowConf = worstCov > COV_LOW_CONFIDENCE;
        LOGGER.info("[Mercurizer] Benchmark complete — GPU: {}/{} MB/s  CPU: {} MOps/s x {}  RT: {} us  CoV: {}{}",
                String.format("%.0f", largeBw), String.format("%.0f", smallBw),
                String.format("%.0f", cpuMOps), cores,
                String.format("%.0f", roundTripNs / 1000.0),
                String.format("%.0f%%", worstCov * 100),
                lowConf ? " [LOW CONFIDENCE]" : "");
        return new MercurizerBenchmarkResult(
                largeBw, smallBw, cpuMOps, cores,
                caps.renderer, caps.version,
                System.currentTimeMillis(), false,
                roundTripNs, worstCov, lowConf,
                LARGE_SIZE, SMALL_SIZE);
    }

    private static double[] runCpuBenchmark() {
        int[] blocks   = new int[CPU_ARRAY_SIZE];
        int[] vertices = new int[CPU_ARRAY_SIZE];
        for (int i = 0; i < CPU_ARRAY_SIZE; i++) blocks[i] = i * 1664525 + 1013904223;
        for (int w = 0; w < CPU_WARMUP; w++) processCpuData(blocks, vertices);
        long[] samples = new long[CPU_RUNS];
        for (int r = 0; r < CPU_RUNS; r++) {
            long t = System.nanoTime();
            processCpuData(blocks, vertices);
            samples[r] = System.nanoTime() - t;
        }
        double meanNs = trimmedMean(removeSpikesByMedian(samples));
        double cov    = coefficientOfVariation(samples);
        return new double[]{ meanNs > 0 ? (CPU_ARRAY_SIZE * 1_000.0) / meanNs : 1.0, cov };
    }

    private static void processCpuData(int[] blocks, int[] vertices) {
        for (int i = 0; i < blocks.length; i++) {
            int block   = blocks[i];
            int state   = (block >>> 4) & 0xFFF;
            int visible = Integer.bitCount(block & 0x3F);
            int x = i & 0xF, y = (i >> 4) & 0xF, z = (i >> 8) & 0xF;
            vertices[i] = (x << 20) | (y << 10) | z | (state << 1) | visible;
            blocks[i]   = (block * 1664525 + 1013904223) & 0x7FFFFFFF;
        }
    }

    private static double bandwidthFromSamples(long[] samples, int bufferSize) {
        double meanNs = trimmedMean(removeSpikesByMedian(samples));
        return meanNs > 0 ? (bufferSize * 1e9) / (meanNs * 1024.0 * 1024.0) : 1.0;
    }

    static long[] removeSpikesByMedian(long[] samples) {
        long[] sorted = Arrays.copyOf(samples, samples.length);
        Arrays.sort(sorted);
        long median = sorted[sorted.length / 2];
        long threshold = (long) (median * SPIKE_FACTOR);
        int valid = 0;
        for (long s : samples) if (s > 0 && s <= threshold) valid++;
        if (valid == 0) return samples;
        long[] out = new long[valid]; int idx = 0;
        for (long s : samples) if (s > 0 && s <= threshold) out[idx++] = s;
        return out;
    }

    static double trimmedMean(long[] samples) {
        long[] sorted = Arrays.copyOf(samples, samples.length);
        Arrays.sort(sorted);
        int lo = (int) Math.floor(sorted.length * TRIM);
        int hi = sorted.length - lo;
        if (hi <= lo) return sorted[sorted.length / 2];
        double sum = 0;
        for (int i = lo; i < hi; i++) sum += sorted[i];
        return sum / (hi - lo);
    }

    static double coefficientOfVariation(long[] raw) {
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

    private static void fillBuffer(ByteBuffer buf) {
        for (int i = 0; i < buf.capacity(); i++) buf.put((byte) (i & 0xFF));
        buf.flip();
    }
}
