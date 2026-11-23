package cc.alpoyy.musicallymods;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MusicallyMods implements ModInitializer {
	public static final String MOD_ID = "musicallymods";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("MusicallyMods initialized (common).");
	}
}
