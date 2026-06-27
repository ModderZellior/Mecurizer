package net.caffeinemc.mods.sodium.mixin.addon;

import net.caffeinemc.mods.sodium.client.MercurizerRuntimePolicy;
import net.caffeinemc.mods.sodium.client.MercurizerTuning;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
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

    @ModifyConstant(method = "updateChunks", constant = @Constant(floatValue = 0.1f, ordinal = 0))
    private float modifyUploadFraction(float ignored) {
        return MercurizerTuning.getUploadFraction();
    }

    @ModifyConstant(method = "updateChunks", constant = @Constant(longValue = 2_000_000L, ordinal = 0))
    private long modifyMinUploadBudget(long ignored) {
        return MercurizerTuning.getMinUploadBudgetNs();
    }
}
