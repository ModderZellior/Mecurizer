package net.caffeinemc.mods.sodium.mixin.addon;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.caffeinemc.mods.sodium.client.MercurizerRuntimePolicy;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.BuilderTaskOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(RenderSectionManager.class)
public class RenderSectionManagerMixin {
    @Shadow private int frame;

    @Inject(method = "tickVisibleRenders", at = @At("HEAD"), cancellable = true)
    private void limitTextureAnimation(CallbackInfo ci) {
        if (!MercurizerRuntimePolicy.shouldAnimateVisibleTexturesThisFrame(this.frame)) {
            ci.cancel();
        }
    }

    @ModifyReturnValue(method = "collectChunkBuildResults", at = @At("RETURN"))
    private ArrayList<BuilderTaskOutput> limitBuildResults(ArrayList<BuilderTaskOutput> original) {
        int max = MercurizerRuntimePolicy.getMaxUploadResultsPerFrame();
        if (original.size() > max) {
            return new ArrayList<>(original.subList(0, max));
        }
        return original;
    }

    @ModifyConstant(method = "updateChunks", constant = @Constant(floatValue = 0.1f, ordinal = 0))
    private float modifyUploadFraction(float ignored) {
        return MercurizerRuntimePolicy.getUploadFraction();
    }

    @ModifyConstant(method = "updateChunks", constant = @Constant(longValue = 2_000_000L, ordinal = 0))
    private long modifyMinUploadBudget(long ignored) {
        return 1_000_000L;
    }
}
