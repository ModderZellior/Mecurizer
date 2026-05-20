package net.caffeinemc.mods.sodium.client.checks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.server.packs.metadata.MetadataSectionType;

/**
 * Reads additional metadata for Mercurizer from a resource pack's `pack.mcmeta` file. This allows the
 * resource pack author to specify which shaders from their pack are not usable with Mercurizer, but that
 * the author is aware of and is fine with being ignored.
 */
public record MercurizerResourcePackMetadata(List<String> ignoredShaders) {
    public static final Codec<MercurizerResourcePackMetadata> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(Codec.STRING.listOf().fieldOf("ignored_shaders")
                    .forGetter(MercurizerResourcePackMetadata::ignoredShaders))
                    .apply(instance, MercurizerResourcePackMetadata::new)
    );
    public static final MetadataSectionType<MercurizerResourcePackMetadata> SERIALIZER =
            new MetadataSectionType<>("mercurizer", CODEC);
}
