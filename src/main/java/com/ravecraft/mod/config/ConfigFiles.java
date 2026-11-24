package com.ravecraft.mod.config;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.core.io.ConfigFolder;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Where the mod's config files are: {@code config/ravecraft/}. */
public final class ConfigFiles {
	private ConfigFiles() {
	}

	/** Moves the config files of older versions into the folder; must run before any of them is read. */
	public static void init() {
		folder().migrate((file, error) -> RaveCraft.LOGGER.warn("Cannot move {} into the ravecraft config folder: {}",
				file, error.toString()));
	}

	/**
	 * Where a config file of the mod is, whether it exists or not.
	 *
	 * @param name the file name
	 * @return the file, in {@code config/ravecraft/}
	 */
	public static Path file(String name) {
		return folder().file(name);
	}

	private static ConfigFolder folder() {
		return new ConfigFolder(FabricLoader.getInstance().getConfigDir());
	}
}
