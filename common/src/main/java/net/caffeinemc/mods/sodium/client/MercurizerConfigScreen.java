package net.caffeinemc.mods.sodium.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

public class MercurizerConfigScreen extends Screen {
    private static final int COLUMN_WIDTH = 200;
    private static final int ROW_HEIGHT = 22;

    private final Screen returnScreen;
    private int leftX;
    private int rightX;
    private int topY;

    public MercurizerConfigScreen(Screen returnScreen) {
        super(Component.literal("Mercurizer Config"));
        this.returnScreen = returnScreen;
    }

    @Override
    protected void init() {
        MercurizerConfig.Values config = MercurizerConfig.get();
        MercurizerBenchmarkResult benchmark = MercurizerTuning.getAppliedResult();
        int cores = Runtime.getRuntime().availableProcessors();

        this.leftX = this.width / 2 - COLUMN_WIDTH - 5;
        this.rightX = this.width / 2 + 5;
        this.topY = 40;

        int y = this.topY;
        addRenderableWidget(new ValueSlider(leftX, y, "GPU upload (large)", 50, 20_000, 50,
                config.gpuLargeMBps,
                () -> benchmark != null ? benchmark.bufferUploadBandwidthMBps : 2_000,
                v -> String.format("%.0f MB/s", v),
                v -> config.gpuLargeMBps = v,
                "Measured speed for large GPU buffer uploads. Mercurizer derives its upload tuning from this."));
        y += ROW_HEIGHT;
        addRenderableWidget(new ValueSlider(leftX, y, "GPU upload (small)", 25, 10_000, 25,
                config.gpuSmallMBps,
                () -> benchmark != null ? benchmark.smallBufferUploadBandwidthMBps : 500,
                v -> String.format("%.0f MB/s", v),
                v -> config.gpuSmallMBps = v,
                "Measured speed for chunk-sized uploads. Sets how long one chunk takes to upload, which decides the minimum upload budget."));
        y += ROW_HEIGHT;
        addRenderableWidget(new ValueSlider(leftX, y, "CPU", 50, 3_000, 10,
                config.cpuMOps,
                () -> benchmark != null ? benchmark.cpuThroughputMOpsPerSec : 400,
                v -> String.format("%.0f MOps/s", v),
                v -> config.cpuMOps = v,
                "Measured CPU speed in million operations per second. A faster CPU gets a larger upload share and less texture animation throttling."));
        y += ROW_HEIGHT;
        addRenderableWidget(new ValueSlider(leftX, y, "Round-trip latency", 0.01, 2.0, 0.01,
                config.roundTripMs,
                () -> benchmark != null ? benchmark.smallBufferRoundTripNs / 1_000_000.0 : 0.2,
                v -> String.format("%.2f ms", v),
                v -> config.roundTripMs = v,
                "Measured driver delay for one small upload. The minimum upload budget never drops below twice this value."));

        y += ROW_HEIGHT + 14;
        addRenderableWidget(new ValueSlider(leftX, y, "Upload share", 0.5, 50, 0.1,
                config.uploadFractionPercent != null ? config.uploadFractionPercent.doubleValue() : null,
                () -> MercurizerTuning.getBenchmarkUploadFraction() * 100.0,
                v -> String.format("%.1f%%", v),
                v -> config.uploadFractionPercent = v != null ? v.floatValue() : null,
                "Share of each frame's time that may be spent uploading chunks. Higher loads chunks faster, lower keeps frame times smoother. Sodium's default is 10%."));
        y += ROW_HEIGHT;
        addRenderableWidget(new ValueSlider(leftX, y, "Min upload budget", 0.05, 5.0, 0.01,
                config.minUploadBudgetMs != null ? config.minUploadBudgetMs.doubleValue() : null,
                () -> MercurizerTuning.getBenchmarkMinUploadBudgetNs() / 1_000_000.0,
                v -> String.format("%.2f ms", v),
                v -> config.minUploadBudgetMs = v != null ? v.floatValue() : null,
                "Upload time every frame gets, even when the upload share would allow less. Sodium's default is 2 ms."));
        y += ROW_HEIGHT;
        int textureOff = MercurizerConfig.TEXTURE_THRESHOLD_MAX_MS + 1;
        addRenderableWidget(new ValueSlider(leftX, y, "Texture animation", MercurizerConfig.TEXTURE_THRESHOLD_MIN_MS, textureOff, 1,
                config.textureAnimThresholdMs != null ? config.textureAnimThresholdMs.doubleValue() : null,
                () -> {
                    long ns = MercurizerTuning.getBenchmarkTextureAnimThresholdNs();
                    return ns == Long.MAX_VALUE ? textureOff : ns / 1_000_000.0;
                },
                v -> v >= textureOff ? "always on" : String.format("pause above %.0f ms", v),
                v -> config.textureAnimThresholdMs = v != null ? v.intValue() : null,
                "Animated textures (water, lava, fire) pause on frames slower than this. Saves CPU time on slow frames. Far right keeps them always animated."));

        y = this.topY;
        addToggle(rightX, y, "Dynamic upload scaling", config.dynamicUploadScaling,
                v -> config.dynamicUploadScaling = v,
                "Lowers the upload budget while frame times are worse than your best, and slowly raises it again when they recover.");
        y += ROW_HEIGHT;
        addToggle(rightX, y, "Anti-stutter recovery", config.antiStutter,
                v -> config.antiStutter = v,
                "After a sudden frame spike, halves chunk uploads for two seconds so the game can catch up.");
        y += ROW_HEIGHT;
        addToggle(rightX, y, "VSync conservatism", config.vsyncConservative,
                v -> config.vsyncConservative = v,
                "Uses a 20% smaller upload share while VSync is on, so uploads are less likely to miss the next screen refresh.");
        y += ROW_HEIGHT;
        addToggle(rightX, y, "Adaptive refinement", config.adaptiveRefinement,
                v -> config.adaptiveRefinement = v,
                "After 30 seconds of stable gameplay, adjusts the upload values to how your PC actually performs in game. Paused while a benchmark measurement is overridden.");

        y += ROW_HEIGHT + 14;
        addRenderableWidget(new ValueSlider(rightX, y, "Chunk builder threads", 1, cores, 1,
                config.chunkBuilderThreads != null ? config.chunkBuilderThreads.doubleValue() : null,
                () -> MercurizerRuntimePolicy.getDefaultChunkWorkerCount(),
                v -> String.format("%.0f", v),
                v -> config.chunkBuilderThreads = v != null ? v.intValue() : null,
                "Threads that build chunk meshes. More threads load chunks faster but compete with the game for CPU time. Applies after rejoining the world."));
        y += ROW_HEIGHT;
        boolean detectedSafe = MercurizerRuntimePolicy.isSafeStagingPathDetected();
        addRenderableWidget(CycleButton.onOffBuilder(config.safeStagingPath != null ? config.safeStagingPath : detectedSafe)
                .withTooltip(v -> Tooltip.create(Component.literal("Slower but safer upload path for old Intel GPUs and AMD drivers that crash with the fast one. Detected for this PC: "
                        + (detectedSafe ? "on" : "off") + ". Applies after restarting the game.")))
                .create(rightX, y, COLUMN_WIDTH, 20, Component.literal("Safe staging path"),
                        (button, value) -> {
                            config.safeStagingPath = value == detectedSafe ? null : value;
                            MercurizerConfig.update(config);
                        }));

        int bottomY = this.height - 28;
        addRenderableWidget(Button.builder(Component.literal("Reset to Benchmark"), button -> {
                    MercurizerConfig.update(new MercurizerConfig.Values());
                    rebuildWidgets();
                })
                .tooltip(Tooltip.create(Component.literal("Removes every setting above and goes back to the values Mercurizer measured.")))
                .bounds(this.width / 2 - 155, bottomY, 150, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(this.width / 2 + 5, bottomY, 150, 20)
                .build());
    }

    private void addToggle(int x, int y, String label, Boolean current, Consumer<Boolean> setter, String tooltip) {
        addRenderableWidget(CycleButton.onOffBuilder(MercurizerConfig.isEnabled(current))
                .withTooltip(v -> Tooltip.create(Component.literal(tooltip)))
                .create(x, y, COLUMN_WIDTH, 20, Component.literal(label),
                        (button, value) -> {
                            setter.accept(value ? null : Boolean.FALSE);
                            MercurizerConfig.update(MercurizerConfig.get());
                        }));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF55);
        graphics.drawString(this.font, "Benchmark measurements", this.leftX, this.topY - 11, 0xFFAAAAAA);
        graphics.drawString(this.font, "Tuning", this.leftX, this.topY + ROW_HEIGHT * 4 + 3, 0xFFAAAAAA);
        graphics.drawString(this.font, "Behaviour", this.rightX, this.topY - 11, 0xFFAAAAAA);
        graphics.drawString(this.font, "Needs rejoin or restart", this.rightX, this.topY + ROW_HEIGHT * 4 + 3, 0xFFAAAAAA);
    }

    @Override
    public void onClose() {
        MercurizerConfig.save();
        this.minecraft.setScreen(this.returnScreen);
    }

    private static class ValueSlider extends AbstractSliderButton {
        private final String label;
        private final double min;
        private final double max;
        private final double step;
        private final DoubleSupplier benchmarkValue;
        private final DoubleFunction<String> format;
        private final Consumer<Double> setter;

        ValueSlider(int x, int y, String label, double min, double max, double step, Double configured,
                    DoubleSupplier benchmarkValue, DoubleFunction<String> format, Consumer<Double> setter, String tooltip) {
            super(x, y, COLUMN_WIDTH, 20, Component.empty(), 0);
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.benchmarkValue = benchmarkValue;
            this.format = format;
            this.setter = setter;
            double start = configured != null ? configured : benchmarkValue.getAsDouble();
            this.value = Math.max(0, Math.min(1, (start - min) / (max - min)));
            setTooltip(Tooltip.create(Component.literal(tooltip)));
            updateMessage();
        }

        private double current() {
            double raw = this.min + this.value * (this.max - this.min);
            return Math.max(this.min, Math.min(this.max, Math.round(raw / this.step) * this.step));
        }

        private boolean matchesBenchmark(double v) {
            return Math.abs(v - this.benchmarkValue.getAsDouble()) < this.step / 2;
        }

        @Override
        protected void updateMessage() {
            double v = current();
            String suffix = matchesBenchmark(v) ? "" : " *";
            setMessage(Component.literal(this.label + ": " + this.format.apply(v) + suffix));
        }

        @Override
        protected void applyValue() {
            double v = current();
            this.setter.accept(matchesBenchmark(v) ? null : v);
            MercurizerConfig.update(MercurizerConfig.get());
        }
    }
}
