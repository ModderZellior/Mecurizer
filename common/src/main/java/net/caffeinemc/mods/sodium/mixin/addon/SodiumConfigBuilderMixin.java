package net.caffeinemc.mods.sodium.mixin.addon;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.caffeinemc.mods.sodium.client.MercurizerConfigIcon;
import net.caffeinemc.mods.sodium.client.gui.SodiumConfigBuilder;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SodiumConfigBuilder.class)
public class SodiumConfigBuilderMixin {

    @ModifyArg(
            method = "createModOptionsBuilder",
            at = @At(value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/api/config/structure/ModOptionsBuilder;setName(Ljava/lang/String;)Lnet/caffeinemc/mods/sodium/api/config/structure/ModOptionsBuilder;"))
    private static String modifyModName(String name) {
        return "Mercurizer";
    }

    @ModifyArg(
            method = "createModOptionsBuilder",
            at = @At(value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/api/config/structure/ModOptionsBuilder;setIcon(Lnet/minecraft/resources/Identifier;)Lnet/caffeinemc/mods/sodium/api/config/structure/ModOptionsBuilder;"))
    private static Identifier modifyModIcon(Identifier icon) {
        return MercurizerConfigIcon.ID;
    }

    @Inject(method = "registerIcon", at = @At("HEAD"))
    private static void registerMercurizerIcon(TextureManager textureManager, CallbackInfo ci) {
        textureManager.registerAndLoad(MercurizerConfigIcon.ID, new MercurizerConfigIcon());
    }
}
