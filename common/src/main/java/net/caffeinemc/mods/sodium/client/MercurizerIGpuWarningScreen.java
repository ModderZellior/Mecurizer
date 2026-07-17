package net.caffeinemc.mods.sodium.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.File;

public class MercurizerIGpuWarningScreen extends Screen {
    private static boolean shownThisSession = false;
    private static final String FLAG_FILE = "mercurizer/no-dgpu.flag";

    private final Screen returnScreen;
    private final String[] lines;

    private static final String[] MSG_VULKAN = {
            "Mercurizer could not detect your GPU type (Vulkan backend).",
            "If Minecraft is running on your integrated GPU, go to",
            "Windows Settings > System > Display > Graphics,",
            "find Minecraft or javaw.exe, and set it to High Performance."
    };

    private static final String[] MSG_WINDOWS = {
            "Mercurizer detected that Minecraft is running on your integrated GPU.",
            "For better performance, go to Windows Settings > System > Display > Graphics,",
            "find Minecraft or javaw.exe, click Options, and set it to High Performance."
    };

    private static final String[] MSG_LINUX = {
            "Mercurizer detected that Minecraft is running on your integrated GPU.",
            "Run Minecraft with DRI_PRIME=1 or set the dedicated GPU",
            "in your desktop environment's graphics settings."
    };

    private static final String[] MSG_MAC = {
            "Mercurizer detected that Minecraft is running on your integrated GPU.",
            "Go to System Preferences > Energy Saver and uncheck Automatic Graphics Switching,",
            "then relaunch Minecraft."
    };

    public MercurizerIGpuWarningScreen(Screen returnScreen, boolean vulkanMode) {
        super(Component.literal(vulkanMode ? "GPU Warning" : "Integrated GPU Detected"));
        this.returnScreen = returnScreen;
        if (vulkanMode) {
            this.lines = MSG_VULKAN;
        } else {
            String platform = MercurizerCapabilities.getOsPlatform();
            this.lines = switch (platform) {
                case "windows" -> MSG_WINDOWS;
                case "mac"     -> MSG_MAC;
                default        -> MSG_LINUX;
            };
        }
    }

    public static boolean shouldShow(MercurizerCapabilities caps) {
        if (shownThisSession) return false;
        if (isNoDgpuFlagged()) return false;
        if (caps == null) return true; // Vulkan backend — show generic warning
        return caps.isIntegratedGpu();
    }

    public static void markShown() {
        shownThisSession = true;
    }

    private static boolean isNoDgpuFlagged() {
        return new File(Minecraft.getInstance().gameDirectory, FLAG_FILE).exists();
    }

    private static void writeNoDgpuFlag() {
        try {
            File f = new File(Minecraft.getInstance().gameDirectory, FLAG_FILE);
            f.getParentFile().mkdirs();
            f.createNewFile();
        } catch (Exception ignored) {}
    }

    @Override
    protected void init() {
        int btnY = this.height / 2 + 50;
        int centerX = this.width / 2;
        this.addRenderableWidget(Button.builder(
                Component.literal("I Understand"),
                btn -> this.onClose())
                .bounds(centerX - 105, btnY, 100, 20)
                .build());
        this.addRenderableWidget(Button.builder(
                Component.literal("I don't have a dGPU"),
                btn -> {
                    writeNoDgpuFlag();
                    this.onClose();
                })
                .bounds(centerX + 5, btnY, 100, 20)
                .build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0xFF000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.centeredText(this.font, this.title.getString(), this.width / 2, 30, 0xFFFFFF55);
        int textY = this.height / 2 - 30;
        for (String line : lines) {
            graphics.centeredText(this.font, line, this.width / 2, textY, 0xFFFFFFFF);
            textY += 12;
        }
    }

    @Override
    public void onClose() {
        shownThisSession = true;
        this.minecraft.setScreenAndShow(this.returnScreen);
    }
}
