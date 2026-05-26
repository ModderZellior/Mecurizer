package net.caffeinemc.mods.sodium.fabric;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MercurizerFabricMod implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mercurizer");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Mercurizer addon active");
    }
}
