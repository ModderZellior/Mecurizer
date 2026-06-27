package net.caffeinemc.mods.sodium.mixin.addon;

import net.caffeinemc.mods.sodium.client.MercurizerBenchmarkController;
import net.caffeinemc.mods.sodium.client.MercurizerBenchmarkScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class MercurizerTitleScreenMixin {
    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void checkBenchmark(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (MercurizerBenchmarkController.shouldShowBenchmarkScreen(mc)) {
            mc.setScreen(new MercurizerBenchmarkScreen((Screen) (Object) this));
            ci.cancel();
        }
    }
}
