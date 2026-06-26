package net.caffeinemc.mods.sodium.mixin.addon;

import net.caffeinemc.mods.sodium.client.MercurizerBenchmarkController;
import net.caffeinemc.mods.sodium.client.MercurizerBenchmarkScreen;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VideoSettingsScreen.class)
public abstract class MercurizerVideoSettingsMixin extends Screen {
    protected MercurizerVideoSettingsMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addRebenchmarkButton(CallbackInfo ci) {
        int bw = 220, bh = 20;
        this.addRenderableWidget(Button.builder(
                Component.literal("Re-run GPU Benchmark"),
                btn -> {
                    MercurizerBenchmarkController.resetForRebenchmark();
                    Minecraft.getInstance().setScreenAndShow(new MercurizerBenchmarkScreen(this));
                })
                .bounds((this.width - bw) / 2, this.height - 30, bw, bh)
                .build());
    }
}
