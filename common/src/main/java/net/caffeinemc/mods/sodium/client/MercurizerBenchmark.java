package net.caffeinemc.mods.sodium.client;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public final class MercurizerBenchmark {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mercurizer");

    private static final int LARGE_SIZE   = 4 * 1024 * 1024;
    private static final int SMALL_SIZE   = 256 * 1024;
    private static final int LARGE_WARMUP = 3;
    private static final int LARGE_RUNS   = 5;
    private static final int SMALL_WARMUP = 5;
    private static final int SMALL_RUNS   = 10;

    private static final int CPU_ARRAY_SIZE = 1024 * 1024;
    private static final int CPU_WARMUP     = 5;
    private static final int CPU_RUNS       = 10;

    public static MercurizerBenchmarkResult run(MercurizerCapabilities caps) {
        if (caps.isVulkan) {
            return runCpuOnly(caps);
        }
        return runFull(caps);
    }

    private static MercurizerBenchmarkResult runCpuOnly(MercurizerCapabilities caps) {
        LOGGER.info("[Mercurizer] Vulkan backend detected — running CPU benchmark only");
        double cpuMOps = runCpuBenchmark();
        int cores = Runtime.getRuntime().availableProcessors();
        LOGGER.info("[Mercurizer]   CPU result: {} MOps/s x {} cores", String.format("%.0f", cpuMOps), cores);
        LOGGER.info("[Mercurizer] Benchmark complete — CPU: {} MOps/s x {} cores (Vulkan, no GPU benchmark)",
                String.format("%.0f", cpuMOps), cores);
        return new MercurizerBenchmarkResult(
                -1, -1,
                cpuMOps, cores,
                "Vulkan", "Vulkan",
                System.currentTimeMillis());
    }

    private static MercurizerBenchmarkResult runFull(MercurizerCapabilities caps) {
        LOGGER.info("[Mercurizer] Starting GPU + CPU benchmark...");
        LOGGER.info("[Mercurizer]   GPU: {}", caps.renderer);
        LOGGER.info("[Mercurizer]   Driver: {}", caps.version);

        int savedBinding = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);

        ByteBuffer largeBuf = MemoryUtil.memAlloc(LARGE_SIZE);
        ByteBuffer smallBuf = MemoryUtil.memAlloc(SMALL_SIZE);
        fillBuffer(largeBuf);
        fillBuffer(smallBuf);

        int largeVbo = GL15.glGenBuffers();
        int smallVbo = GL15.glGenBuffers();

        double largeBandwidth;
        double smallBandwidth;

        try {
            LOGGER.info("[Mercurizer]   Running GPU large-buffer benchmark ({} MB x {} runs)...",
                    LARGE_SIZE / (1024 * 1024), LARGE_RUNS);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, largeVbo);
            for (int i = 0; i < LARGE_WARMUP; i++) {
                largeBuf.rewind();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, largeBuf, GL15.GL_STREAM_DRAW);
            }
            GL11.glFinish();

            long t0 = System.nanoTime();
            for (int i = 0; i < LARGE_RUNS; i++) {
                largeBuf.rewind();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, largeBuf, GL15.GL_STREAM_DRAW);
            }
            GL11.glFinish();
            long largeDurationNs = System.nanoTime() - t0;

            largeBandwidth = (LARGE_SIZE * (double) LARGE_RUNS * 1_000_000_000.0)
                    / (largeDurationNs * 1024.0 * 1024.0);
            LOGGER.info("[Mercurizer]   Large buffer result: {} MB/s", String.format("%.0f", largeBandwidth));

            LOGGER.info("[Mercurizer]   Running GPU small-buffer benchmark ({} KB x {} runs)...",
                    SMALL_SIZE / 1024, SMALL_RUNS);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, smallVbo);
            for (int i = 0; i < SMALL_WARMUP; i++) {
                smallBuf.rewind();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, smallBuf, GL15.GL_STREAM_DRAW);
            }
            GL11.glFinish();

            long t1 = System.nanoTime();
            for (int i = 0; i < SMALL_RUNS; i++) {
                smallBuf.rewind();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, smallBuf, GL15.GL_STREAM_DRAW);
            }
            GL11.glFinish();
            long smallDurationNs = System.nanoTime() - t1;

            smallBandwidth = (SMALL_SIZE * (double) SMALL_RUNS * 1_000_000_000.0)
                    / (smallDurationNs * 1024.0 * 1024.0);
            LOGGER.info("[Mercurizer]   Small buffer result: {} MB/s", String.format("%.0f", smallBandwidth));
        } finally {
            MemoryUtil.memFree(largeBuf);
            MemoryUtil.memFree(smallBuf);
            GL15.glDeleteBuffers(largeVbo);
            GL15.glDeleteBuffers(smallVbo);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, savedBinding);
        }

        LOGGER.info("[Mercurizer]   Running CPU benchmark ({} MB working set x {} runs)...",
                (CPU_ARRAY_SIZE * 4) / (1024 * 1024), CPU_RUNS);
        double cpuMOps = runCpuBenchmark();
        int cores = Runtime.getRuntime().availableProcessors();
        LOGGER.info("[Mercurizer]   CPU result: {} MOps/s x {} cores", String.format("%.0f", cpuMOps), cores);

        LOGGER.info("[Mercurizer] Benchmark complete — GPU: {}/{} MB/s  CPU: {} MOps/s x {} cores",
                String.format("%.0f", largeBandwidth),
                String.format("%.0f", smallBandwidth),
                String.format("%.0f", cpuMOps),
                cores);

        return new MercurizerBenchmarkResult(
                largeBandwidth, smallBandwidth,
                cpuMOps, cores,
                caps.renderer, caps.version,
                System.currentTimeMillis());
    }

    private static double runCpuBenchmark() {
        int[] blocks   = new int[CPU_ARRAY_SIZE];
        int[] vertices = new int[CPU_ARRAY_SIZE];
        for (int i = 0; i < CPU_ARRAY_SIZE; i++) blocks[i] = i * 1664525 + 1013904223;

        for (int w = 0; w < CPU_WARMUP; w++) processCpuData(blocks, vertices);

        long t0 = System.nanoTime();
        long totalOps = 0;
        for (int r = 0; r < CPU_RUNS; r++) totalOps += processCpuData(blocks, vertices);
        long elapsed = System.nanoTime() - t0;

        return (totalOps * 1_000.0) / elapsed;
    }

    private static long processCpuData(int[] blocks, int[] vertices) {
        for (int i = 0; i < blocks.length; i++) {
            int block   = blocks[i];
            int state   = (block >>> 4) & 0xFFF;
            int visible = Integer.bitCount(block & 0x3F);
            int x = i & 0xF, y = (i >> 4) & 0xF, z = (i >> 8) & 0xF;
            vertices[i] = (x << 20) | (y << 10) | z | (state << 1) | visible;
            blocks[i]   = (block * 1664525 + 1013904223) & 0x7FFFFFFF;
        }
        return blocks.length;
    }

    private static void fillBuffer(ByteBuffer buf) {
        for (int i = 0; i < buf.capacity(); i++) buf.put((byte) (i & 0xFF));
        buf.flip();
    }
}
