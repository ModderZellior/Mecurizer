package net.caffeinemc.mods.sodium.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class MercurizerBenchmarkScreen extends Screen {
    private static final long WARMUP_LINGER_NS = 60_000_000L;
    private static final long RESULT_LINGER_NS = 3_000_000_000L;

    private static final int LARGE_SIZE    = 4 * 1024 * 1024;
    private static final int SMALL_SIZE    = 256 * 1024;
    private static final int LARGE_WARMUP  = 3;
    private static final int LARGE_RUNS    = 5;
    private static final int SMALL_WARMUP  = 5;
    private static final int SMALL_RUNS    = 10;
    private static final int CPU_ARRAY_SIZE = 1024 * 1024;
    private static final int CPU_WARMUP    = 5;
    private static final int CPU_RUNS      = 10;

    private enum Phase { LINGER, INIT, WARMUP_LARGE, MEASURE_LARGE, WARMUP_SMALL, MEASURE_SMALL, CPU_WAIT, RESULT }

    private final Screen parent;
    private final MercurizerCapabilities caps;
    private Phase phase = Phase.LINGER;
    private long phaseStartNs = -1;
    private int step = 0;

    private int largeVbo = -1, smallVbo = -1, savedBinding = 0;
    private ByteBuffer largeBuf, smallBuf;
    private double largeBw, smallBw;

    private Thread cpuThread;
    private volatile double cpuMOps = -1;

    private MercurizerBenchmarkResult result;
    private long resultSinceNs;

    public MercurizerBenchmarkScreen(Screen parent) {
        super(Component.literal("Mercurizer"));
        this.parent = parent;
        MercurizerCapabilities c = MercurizerCapabilities.getCached();
        this.caps = c != null ? c : MercurizerCapabilities.probeAndCache();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        if (phaseStartNs < 0) phaseStartNs = now;

        graphics.fill(0, 0, this.width, this.height, 0xFF000000);

        switch (phase) {
            case LINGER:
                draw(graphics, 0f, "Mercurizer — Benchmarking GPU & CPU...", "This runs once per GPU or driver update.");
                if (now - phaseStartNs >= WARMUP_LINGER_NS) advance(Phase.INIT);
                break;

            case INIT:
                savedBinding = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
                largeBuf = MemoryUtil.memAlloc(LARGE_SIZE);
                smallBuf = MemoryUtil.memAlloc(SMALL_SIZE);
                fill(largeBuf); fill(smallBuf);
                largeVbo = GL15.glGenBuffers();
                smallVbo = GL15.glGenBuffers();
                advance(Phase.WARMUP_LARGE);
                draw(graphics, 0.05f, "Mercurizer — Benchmarking GPU & CPU...", "Initialising...");
                break;

            case WARMUP_LARGE:
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, largeVbo);
                largeBuf.rewind();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, largeBuf, GL15.GL_STREAM_DRAW);
                step++;
                draw(graphics, 0.05f + (step / (float) LARGE_WARMUP) * 0.20f,
                        "Mercurizer — Benchmarking GPU & CPU...", "Warming up large buffer...");
                if (step >= LARGE_WARMUP) { GL11.glFinish(); advance(Phase.MEASURE_LARGE); }
                break;

            case MEASURE_LARGE: {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, largeVbo);
                long t = System.nanoTime();
                for (int i = 0; i < LARGE_RUNS; i++) { largeBuf.rewind(); GL15.glBufferData(GL15.GL_ARRAY_BUFFER, largeBuf, GL15.GL_STREAM_DRAW); }
                GL11.glFinish();
                largeBw = (LARGE_SIZE * (double) LARGE_RUNS * 1e9) / ((System.nanoTime() - t) * 1024.0 * 1024.0);
                advance(Phase.WARMUP_SMALL);
                draw(graphics, 0.35f, "Mercurizer — Benchmarking GPU & CPU...", String.format("Large buffer: %.0f MB/s", largeBw));
                break;
            }

            case WARMUP_SMALL:
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, smallVbo);
                smallBuf.rewind();
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, smallBuf, GL15.GL_STREAM_DRAW);
                step++;
                draw(graphics, 0.35f + (step / (float) SMALL_WARMUP) * 0.20f,
                        "Mercurizer — Benchmarking GPU & CPU...", String.format("Large: %.0f MB/s — Warming up small buffer...", largeBw));
                if (step >= SMALL_WARMUP) { GL11.glFinish(); advance(Phase.MEASURE_SMALL); }
                break;

            case MEASURE_SMALL: {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, smallVbo);
                long t = System.nanoTime();
                for (int i = 0; i < SMALL_RUNS; i++) { smallBuf.rewind(); GL15.glBufferData(GL15.GL_ARRAY_BUFFER, smallBuf, GL15.GL_STREAM_DRAW); }
                GL11.glFinish();
                smallBw = (SMALL_SIZE * (double) SMALL_RUNS * 1e9) / ((System.nanoTime() - t) * 1024.0 * 1024.0);
                MemoryUtil.memFree(largeBuf); MemoryUtil.memFree(smallBuf);
                GL15.glDeleteBuffers(largeVbo); GL15.glDeleteBuffers(smallVbo);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, savedBinding);
                cpuThread = new Thread(this::runCpu, "Mercurizer-CPU-Bench");
                cpuThread.setDaemon(true);
                cpuThread.start();
                advance(Phase.CPU_WAIT);
                draw(graphics, 0.60f, "Mercurizer — Benchmarking CPU...", String.format("GPU: %.0f / %.0f MB/s", largeBw, smallBw));
                break;
            }

            case CPU_WAIT:
                float cpuProg = cpuMOps >= 0 ? 0.95f
                        : Math.min(0.94f, 0.60f + (now - phaseStartNs) / 3_000_000_000f * 0.35f);
                draw(graphics, cpuProg, "Mercurizer — Benchmarking CPU...",
                        String.format("GPU: %.0f / %.0f MB/s — Running CPU benchmark...", largeBw, smallBw));
                if (cpuMOps >= 0) finalizeBenchmark();
                break;

            case RESULT:
                if (now - resultSinceNs < RESULT_LINGER_NS) {
                    draw(graphics, 1f,
                            String.format("Mercurizer — Done. GPU: %.0f MB/s  |  CPU: %.0f MOps/s x %d cores",
                                    result.bufferUploadBandwidthMBps, result.cpuThroughputMOpsPerSec, result.availableProcessors),
                            String.format("Upload: %.1f%%  |  Min budget: %.3f ms  |  Synthetic",
                                    MercurizerTuning.getUploadFraction() * 100,
                                    MercurizerTuning.getMinUploadBudgetNs() / 1_000_000.0));
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

    private void finalizeBenchmark() {
        result = new MercurizerBenchmarkResult(
                largeBw, smallBw, cpuMOps, Runtime.getRuntime().availableProcessors(),
                caps.renderer, caps.version, System.currentTimeMillis(), false);
        MercurizerBenchmarkStore.save(result, Minecraft.getInstance().gameDirectory);
        MercurizerTuning.apply(result);
        MercurizerBenchmarkController.markDone();
        resultSinceNs = System.nanoTime();
        phase = Phase.RESULT;
    }

    private void runCpu() {
        int[] blocks = new int[CPU_ARRAY_SIZE];
        int[] vertices = new int[CPU_ARRAY_SIZE];
        for (int i = 0; i < CPU_ARRAY_SIZE; i++) blocks[i] = i * 1664525 + 1013904223;
        for (int w = 0; w < CPU_WARMUP; w++) processCpu(blocks, vertices);
        long t = System.nanoTime();
        long ops = 0;
        for (int r = 0; r < CPU_RUNS; r++) ops += processCpu(blocks, vertices);
        cpuMOps = (ops * 1_000.0) / (System.nanoTime() - t);
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

    private void fill(ByteBuffer buf) {
        for (int i = 0; i < buf.capacity(); i++) buf.put((byte) (i & 0xFF));
        buf.flip();
    }

    private void draw(GuiGraphics g, float progress, String msg, String sub) {
        int bw = 320, bh = 8, bx = (this.width - bw) / 2, by = this.height / 2 - 4;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF555555);
        g.fill(bx, by, bx + bw, by + bh, 0xFF222222);
        if (progress > 0f)
            g.fill(bx, by, bx + Math.max(0, Math.min(bw, (int) (bw * progress))), by + bh, 0xFF55FF55);
        g.drawCenteredString(this.font, msg, this.width / 2, by - 16, 0xFFFFFF);
        g.drawCenteredString(this.font, sub, this.width / 2, by + bh + 8, 0x888888);
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}
