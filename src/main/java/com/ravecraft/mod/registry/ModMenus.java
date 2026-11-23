package com.ravecraft.mod.registry;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.menu.MachineMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;

/** Menu types. */
public final class ModMenus {
	/** Every machine's menu; the client receives the machine's position. */
	public static final MenuType<MachineMenu> MACHINE = Registry.register(BuiltInRegistries.MENU,
			RaveCraft.id("machine"), new ExtendedMenuType<MachineMenu, BlockPos>(MachineMenu::new,
					BlockPos.STREAM_CODEC));

	private ModMenus() {
	}

	/** Forces class loading, hence registration. */
	public static void init() {
	}
}
