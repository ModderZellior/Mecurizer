package net.caffeinemc.mods.sodium.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MercurizerFabricMod implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mercurizer");

    @Override
    public void onInitializeClient() {
        String sodiumVersion = FabricLoader.getInstance()
                .getModContainer("sodium")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("not found");
        LOGGER.info("[Mercurizer] Active — Sodium version: {}", sodiumVersion);
    }
}
