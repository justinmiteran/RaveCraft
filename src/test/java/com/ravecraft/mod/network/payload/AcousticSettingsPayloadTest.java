package com.ravecraft.mod.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ravecraft.mod.core.acoustics.AcousticSettings;
import com.ravecraft.mod.core.acoustics.DeviceProfile;
import com.ravecraft.mod.core.acoustics.Directivity;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class AcousticSettingsPayloadTest {
	@Test
	void codecRoundTrip() {
		AcousticSettingsPayload payload = new AcousticSettingsPayload(
				new AcousticSettings(0.5, 0.3, 0.1, new DeviceProfile(48, -9, 120, Directivity.OMNI)));
		ByteBuf buf = Unpooled.buffer();
		AcousticSettingsPayload.CODEC.encode(buf, payload);
		assertEquals(payload, AcousticSettingsPayload.CODEC.decode(buf));
		assertEquals(0, buf.readableBytes());
	}
}
