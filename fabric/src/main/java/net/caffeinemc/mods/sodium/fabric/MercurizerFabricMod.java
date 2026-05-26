package net.caffeinemc.mods.sodium.fabric;

import net.fabricmc.api.ClientModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MercurizerFabricMod implements ClientModInitializer {
    private static final Logger LOGGER = LogManager.getLogger("Mercurizer");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Mercurizer addon active");
    }
}
