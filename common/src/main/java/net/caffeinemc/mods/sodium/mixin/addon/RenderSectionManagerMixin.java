package net.caffeinemc.mods.sodium.mixin.addon;

import net.caffeinemc.mods.sodium.client.MercurizerRuntimePolicy;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSectionManager.class)
public class RenderSectionManagerMixin {
    @Shadow private long lastFrameDuration;

    @Inject(method = "tickVisibleRenders", at = @At("HEAD"), cancellable = true)
    private void limitTextureAnimation(CallbackInfo ci) {
        if (!MercurizerRuntimePolicy.shouldAnimateVisibleTexturesThisFrame(this.lastFrameDuration)) {
            ci.cancel();
        }
    }
}
