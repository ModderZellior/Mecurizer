package net.caffeinemc.mods.sodium.mixin.addon;

import net.caffeinemc.mods.sodium.client.MercurizerConfigIcon;
import net.caffeinemc.mods.sodium.client.gui.SodiumConfigBuilder;
import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SodiumConfigBuilder.class)
public class SodiumConfigBuilderMixin {

    @Inject(method = "registerIcon", at = @At("HEAD"))
    private static void registerMercurizerIcon(TextureManager textureManager, CallbackInfo ci) {
        textureManager.registerAndLoad(MercurizerConfigIcon.ID, new MercurizerConfigIcon());
    }
}
