package com.ravecraft.mod.gametest;

import com.ravecraft.mod.component.SpeakerData;
import com.ravecraft.mod.core.speaker.SpeakerSettings;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

public class PortableSpeakerGameTest {
	@GameTest
	public void speakerDataCodecRoundTrip(GameTestHelper helper) {
		SpeakerData data = new SpeakerData(UUID.randomUUID(), new SpeakerSettings(true, 42));
		var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		Tag encoded = SpeakerData.CODEC.encodeStart(ops, data).getOrThrow();
		helper.assertValueEqual(SpeakerData.CODEC.parse(ops, encoded).getOrThrow(), data, "decoded");
		helper.succeed();
	}
}
