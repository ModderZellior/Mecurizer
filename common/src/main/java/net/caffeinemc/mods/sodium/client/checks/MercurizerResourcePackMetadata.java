package net.caffeinemc.mods.sodium.client.checks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.server.packs.metadata.MetadataSectionType;

public record MercurizerResourcePackMetadata(List<String> ignoredShaders) {
    public static final Codec<MercurizerResourcePackMetadata> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(Codec.STRING.listOf().fieldOf("ignored_shaders")
                    .forGetter(MercurizerResourcePackMetadata::ignoredShaders))
                    .apply(instance, MercurizerResourcePackMetadata::new)
    );
    public static final MetadataSectionType<MercurizerResourcePackMetadata> SERIALIZER =
            new MetadataSectionType<>("mercurizer", CODEC);
}
