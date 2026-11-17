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
				new AcousticSettings(0.5, 0.3, 0.1, 60, 128, new DeviceProfile(40, 90, -9, 120, Directivity.OMNI)));
		ByteBuf buf = Unpooled.buffer();
		AcousticSettingsPayload.CODEC.encode(buf, payload);
		assertEquals(payload, AcousticSettingsPayload.CODEC.decode(buf));
		assertEquals(0, buf.readableBytes());
	}

	@Test
	void decodeBoundsValuesFromServer() {
		AcousticSettingsPayload payload = new AcousticSettingsPayload(
				new AcousticSettings(Double.NaN, 0.3, -1, 68, 9999, new DeviceProfile(20, 85, -9, 30_000, Directivity.OMNI)));
		ByteBuf buf = Unpooled.buffer();
		AcousticSettingsPayload.CODEC.encode(buf, payload);
		AcousticSettings decoded = AcousticSettingsPayload.CODEC.decode(buf).settings();
		assertEquals(0.25, decoded.rolloff());
		assertEquals(0, decoded.airAbsorptionDbPerBlock());
		assertEquals(512, decoded.maxRange());
		assertEquals(500, decoded.portable().bassCutHz());
	}
}
