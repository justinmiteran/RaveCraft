package com.ravecraft.mod.gametest;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.block.MachineBlock;
import com.ravecraft.mod.registry.ModBlocks;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvent;

/** Each machine plays its own recorded loop while it works. */
public class MachineSoundGameTest {
	@GameTest
	public void everyMachinePlaysItsOwnLoop(GameTestHelper h) {
		Map<String, MachineBlock> machines = Map.of("coil_winder", (MachineBlock) ModBlocks.COIL_WINDER,
				"magnetizer", (MachineBlock) ModBlocks.MAGNETIZER, "extruder", (MachineBlock) ModBlocks.EXTRUDER,
				"etcher", (MachineBlock) ModBlocks.ETCHER);
		machines.forEach((name, machine) -> {
			SoundEvent sound = machine.effects().sound();
			h.assertValueEqual(RaveCraft.id("machine." + name), sound.location(), name + " sound");
			h.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(sound.location()), name + " sound registered");
		});
		h.succeed();
	}
}
