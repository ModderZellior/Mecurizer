package net.caffeinemc.mods.sodium.mixin.addon;

import net.caffeinemc.mods.sodium.client.checks.MercurizerResourcePackMetadata;
import net.minecraft.server.packs.PackResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.checks.ResourcePackScanner")
public class ResourcePackScannerMixin {
    @Inject(method = "determineIgnoredShaders", at = @At("RETURN"))
    private static void addMercurizerIgnoredShaders(
            PackResources resourcePack,
            CallbackInfoReturnable<List<String>> cir) {
        try {
            var meta = resourcePack.getMetadataSection(MercurizerResourcePackMetadata.SERIALIZER);
            if (meta != null && !meta.ignoredShaders().isEmpty()) {
                var combined = new ArrayList<>(cir.getReturnValue());
                combined.addAll(meta.ignoredShaders());
                cir.setReturnValue(combined);
            }
        } catch (IOException ignored) {
        }
    }
}
