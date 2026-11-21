package com.ravecraft.mod.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ravecraft.mod.core.speaker.CarryPlace;
import com.ravecraft.mod.core.speaker.SpeakerKind;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.VarInt;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class SpeakerHostPayloadTest {
	private static final UUID SPEAKER = UUID.fromString("5b7e2c41-9a0d-4f3e-8c62-1d4a7e9b0f23");
	private static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION,
			Identifier.withDefaultNamespace("overworld"));

	private static SpeakerHostPayload roundTrip(SpeakerHostPayload payload) {
		ByteBuf buf = Unpooled.buffer();
		SpeakerHostPayload.CODEC.encode(buf, payload);
		SpeakerHostPayload decoded = SpeakerHostPayload.CODEC.decode(buf);
		assertEquals(0, buf.readableBytes(), "fully read");
		return decoded;
	}

	@Test
	void roundTripsKindAndPlace() {
		SpeakerHostPayload carried = new SpeakerHostPayload(SPEAKER, OVERWORLD, Optional.empty(), 17,
				SpeakerKind.PARTY, Optional.of(CarryPlace.BACK), 55, true);
		assertEquals(carried, roundTrip(carried));
	}

	@Test
	void roundTripsBlockWithoutPlace() {
		SpeakerHostPayload block = new SpeakerHostPayload(SPEAKER, OVERWORLD, Optional.of(new BlockPos(1, 2, 3)),
				SpeakerHostPayload.NO_ENTITY, SpeakerKind.PORTABLE, Optional.empty(), 0, false);
		assertEquals(block, roundTrip(block));
	}

	@Test
	void unknownKindIsRejected() {
		ByteBuf buf = Unpooled.buffer();
		VarInt.write(buf, SpeakerKind.values().length);
		assertThrows(DecoderException.class, () -> SpeakerHostPayload.KIND_CODEC.decode(buf));
	}
}
