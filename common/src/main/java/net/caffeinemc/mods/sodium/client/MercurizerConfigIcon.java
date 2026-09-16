package net.caffeinemc.mods.sodium.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;

public class MercurizerConfigIcon extends ReloadableTexture {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("mercurizer", "textures/gui/config-icon.png");

    public MercurizerConfigIcon() {
        super(ID);
    }

    @Override
    public TextureContents loadContents(ResourceManager resourceManager) throws IOException {
        try (var stream = MercurizerConfigIcon.class.getResourceAsStream("/assets/mercurizer/textures/gui/config-icon.png")) {
            return new TextureContents(NativeImage.read(stream), new TextureMetadataSection(false, false, MipmapStrategy.AUTO, 0.1f));
        }
    }
}
