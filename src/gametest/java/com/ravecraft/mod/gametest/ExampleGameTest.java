package com.ravecraft.mod.gametest;

import com.ravecraft.mod.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * In-game tests on a real server, run by {@code ./gradlew build} (task {@code runGameTest}). Register each class in
 * {@code src/gametest/resources/fabric.mod.json}.
 */
public class ExampleGameTest {
	@GameTest
	public void exampleBlockCanBePlaced(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, ModBlocks.EXAMPLE_BLOCK);
		helper.assertBlockPresent(ModBlocks.EXAMPLE_BLOCK, pos);
		helper.succeed();
	}
}
