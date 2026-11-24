package com.ravecraft.mod.gametest;

import com.ravecraft.mod.blockentity.MachineBlockEntity;
import com.ravecraft.mod.menu.MachineBookType;
import com.ravecraft.mod.menu.MachineMenu;
import com.ravecraft.mod.registry.ModBlocks;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.stats.RecipeBookSettings;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.level.GameType;

public class MachineBookGameTest {
	private static final BlockPos POS = new BlockPos(1, 2, 1);

	@GameTest
	public void machineMenuUsesTheMachineBook(GameTestHelper h) {
		Player player = h.makeMockPlayer(GameType.SURVIVAL);
		h.setBlock(POS, ModBlocks.COIL_WINDER);
		MachineMenu menu = new MachineMenu(0, player.getInventory(),
				h.getBlockEntity(POS, MachineBlockEntity.class));
		h.assertValueEqual(menu.getRecipeBookType(), MachineBookType.MACHINE, "machine book");
		h.assertTrue(Arrays.asList(RecipeBookType.values()).contains(MachineBookType.MACHINE), "a real book type");
		h.succeed();
	}

	@GameTest
	public void machineSettingsAreSeparate(GameTestHelper h) {
		RecipeBookSettings settings = new RecipeBookSettings();
		settings.setOpen(MachineBookType.MACHINE, true);
		settings.setFiltering(MachineBookType.MACHINE, true);
		h.assertTrue(settings.isOpen(MachineBookType.MACHINE), "machine book open");
		h.assertFalse(settings.isOpen(RecipeBookType.FURNACE), "furnace book untouched");
		h.assertFalse(settings.isFiltering(RecipeBookType.FURNACE), "furnace filter untouched");
		RecipeBookSettings copy = settings.copy();
		h.assertTrue(copy.isOpen(MachineBookType.MACHINE) && copy.isFiltering(MachineBookType.MACHINE), "copied");
		RecipeBookSettings other = new RecipeBookSettings();
		other.replaceFrom(settings);
		h.assertTrue(other.isOpen(MachineBookType.MACHINE), "replaced");
		h.succeed();
	}

	@GameTest
	public void machineSettingsAreSaved(GameTestHelper h) {
		RecipeBookSettings settings = new RecipeBookSettings();
		settings.setOpen(MachineBookType.MACHINE, true);
		Tag saved = RecipeBookSettings.MAP_CODEC.codec().encodeStart(NbtOps.INSTANCE, settings).getOrThrow();
		RecipeBookSettings loaded = RecipeBookSettings.MAP_CODEC.codec().parse(NbtOps.INSTANCE, saved).getOrThrow();
		h.assertTrue(loaded.isOpen(MachineBookType.MACHINE), "open state saved");
		h.assertFalse(loaded.isOpen(RecipeBookType.FURNACE), "furnace still closed");
		h.succeed();
	}

	@GameTest
	public void machineSettingsAreSent(GameTestHelper h) {
		RecipeBookSettings settings = new RecipeBookSettings();
		settings.setFiltering(MachineBookType.MACHINE, true);
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		RecipeBookSettings.STREAM_CODEC.encode(buf, settings);
		RecipeBookSettings received = RecipeBookSettings.STREAM_CODEC.decode(buf);
		h.assertTrue(received.isFiltering(MachineBookType.MACHINE), "filter sent to the client");
		h.assertValueEqual(buf.readableBytes(), 0, "everything read");
		h.succeed();
	}
}
