package net.caffeinemc.mods.sodium.mixin.addon;

import net.caffeinemc.mods.sodium.client.MercurizerFrameTracker;
import net.caffeinemc.mods.sodium.client.MercurizerRuntimePolicy;
import net.caffeinemc.mods.sodium.client.MercurizerTuning;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSectionManager.class)
public class RenderSectionManagerMixin {
    @Shadow private long lastFrameDuration;
    @Unique private long merc$uploadStartNs = -1;

    @Inject(method = "updateChunks", at = @At("HEAD"))
    private void recordFrameTime(CallbackInfo ci) {
        MercurizerFrameTracker.onFrameStart();
        MercurizerTuning.checkRefinement(Minecraft.getInstance().gameDirectory);
        merc$uploadStartNs = System.nanoTime();
    }

    @Inject(method = "updateChunks", at = @At("RETURN"))
    private void recordUploadComplete(CallbackInfo ci) {
        if (merc$uploadStartNs > 0) {
            long uploadNs = System.nanoTime() - merc$uploadStartNs;
            long targetNs = this.lastFrameDuration > 0 ? this.lastFrameDuration : 16_666_666L;
            MercurizerFrameTracker.onUploadComplete(uploadNs, targetNs);
        }
    }

    @Inject(method = "tickVisibleRenders", at = @At("HEAD"), cancellable = true)
    private void limitTextureAnimation(CallbackInfo ci) {
        if (!MercurizerRuntimePolicy.shouldAnimateVisibleTexturesThisFrame(this.lastFrameDuration)) {
            ci.cancel();
        }
    }

    @ModifyConstant(method = "updateChunks", constant = @Constant(floatValue = 0.3f, ordinal = 0))
    private float modifyUploadFraction(float ignored) {
        return MercurizerTuning.getUploadFraction();
    }

    @ModifyConstant(method = "updateChunks", constant = @Constant(longValue = 10_000_000L, ordinal = 0))
    private long modifyMinUploadBudget(long ignored) {
        return MercurizerTuning.getMinUploadBudgetNs();
    }
}
