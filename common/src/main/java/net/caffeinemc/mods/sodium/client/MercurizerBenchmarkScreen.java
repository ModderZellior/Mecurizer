package net.caffeinemc.mods.sodium.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MercurizerBenchmarkScreen extends Screen {
    private static final long WARMUP_LINGER_NS = 60_000_000L;    // 60 ms before benchmark starts
    private static final long RESULT_LINGER_NS = 3_000_000_000L; // 3 s showing results

    private final Screen parent;
    private long firstRenderNs = -1;
    private boolean benchmarkStarted = false;
    private MercurizerBenchmarkResult result = null;
    private long doneSinceNs = -1;

    public MercurizerBenchmarkScreen(Screen parent) {
        super(Component.literal("Mercurizer"));
        this.parent = parent;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        if (firstRenderNs < 0) firstRenderNs = now;

        graphics.fill(0, 0, this.width, this.height, 0xFF000000);

        if (!benchmarkStarted) {
            drawProgress(graphics, 0f, "Mercurizer — Benchmarking GPU & CPU...", "This runs once per GPU or driver update.");
            if (now - firstRenderNs >= WARMUP_LINGER_NS) {
                benchmarkStarted = true;
                MercurizerCapabilities caps = MercurizerCapabilities.getCached();
                if (caps == null) caps = MercurizerCapabilities.probeAndCache();
                result = MercurizerBenchmark.run(caps);
                MercurizerBenchmarkStore.save(result, Minecraft.getInstance().gameDirectory);
                MercurizerTuning.apply(result);
                MercurizerBenchmarkController.markDone();
                doneSinceNs = System.nanoTime();
            }
        } else if (now - doneSinceNs < RESULT_LINGER_NS) {
            drawProgress(graphics, 1f,
                    String.format("Mercurizer — Done. GPU: %.0f MB/s  |  CPU: %.0f MOps/s x %d cores",
                            result.bufferUploadBandwidthMBps,
                            result.cpuThroughputMOpsPerSec,
                            result.availableProcessors),
                    String.format("Upload fraction: %.0f%%  |  Min budget: %.2f ms",
                            MercurizerTuning.getUploadFraction() * 100,
                            MercurizerTuning.getMinUploadBudgetNs() / 1_000_000.0));
        } else {
            Minecraft.getInstance().setScreenAndShow(parent);
        }
    }

    private void drawProgress(GuiGraphicsExtractor graphics, float progress, String message, String subtext) {
        int barW = 320, barH = 8;
        int barX = (this.width - barW) / 2;
        int barY = this.height / 2 - 4;

        graphics.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xFF555555);
        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF222222);
        if (progress > 0f) {
            int fillW = Math.max(0, Math.min(barW, (int) (barW * progress)));
            graphics.fill(barX, barY, barX + fillW, barY + barH, 0xFF55FF55);
        }

        graphics.centeredText(this.font, message, this.width / 2, barY - 16, 0xFFFFFF);
        graphics.centeredText(this.font, subtext, this.width / 2, barY + barH + 8, 0x888888);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
