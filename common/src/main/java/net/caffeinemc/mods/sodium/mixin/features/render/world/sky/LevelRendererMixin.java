package net.caffeinemc.mods.sodium.mixin.features.render.world.sky;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow
    protected abstract boolean doesMobEffectBlockSky(Camera camera);

    /**
     * Prevents the sky layer from rendering when the fog distance is reduced
     * from the default.
     */
    @WrapMethod(method = "doesMobEffectBlockSky")
    private boolean preRenderSky(Camera camera, Operation<Boolean> original) {
        if (Minecraft.getInstance().gameRenderer.getMainCamera().getFluidInCamera() != FogType.NONE) {
            return true;
        }

        return original.call(camera);
    }
}
