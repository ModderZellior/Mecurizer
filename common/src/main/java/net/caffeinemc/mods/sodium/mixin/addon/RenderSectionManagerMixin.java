package net.caffeinemc.mods.sodium.mixin.addon;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.caffeinemc.mods.sodium.client.MercurizerRuntimePolicy;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.BuilderTaskOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(RenderSectionManager.class)
public class RenderSectionManagerMixin {
    @Shadow private long lastFrameDuration;

    @Inject(method = "tickVisibleRenders", at = @At("HEAD"), cancellable = true)
    private void limitTextureAnimation(CallbackInfo ci) {
        if (!MercurizerRuntimePolicy.shouldAnimateVisibleTexturesThisFrame(this.lastFrameDuration)) {
            ci.cancel();
        }
    }

    @ModifyReturnValue(method = "collectChunkBuildResults", at = @At("RETURN"))
    private ArrayList<BuilderTaskOutput> limitBuildResults(ArrayList<BuilderTaskOutput> original) {
        int max = MercurizerRuntimePolicy.getMaxUploadResultsPerFrame();
        if (original.size() > max) {
            original.subList(max, original.size()).clear();
        }
        return original;
    }
}
