package com.ravecraft.mod.core.audio;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Unsigned and tag reads over audio file headers, at absolute positions of a {@link ByteBuffer}. */
public final class Bytes {
	/** Length of a four-character code such as {@code RIFF} or {@code fLaC}. */
	public static final int FOUR_CC = 4;
	/** Bits kept from each byte of an ID3v2 syncsafe integer. */
	private static final int SYNCSAFE_BITS = 7;
	private static final int SYNCSAFE_MASK = 0x7F;

	private Bytes() {
	}

	/**
	 * Reads one byte as unsigned.
	 *
	 * @param b the buffer
	 * @param at the absolute position
	 * @return the byte, from 0 to 255
	 */
	public static int u8(ByteBuffer b, int at) {
		return Byte.toUnsignedInt(b.get(at));
	}

	/**
	 * Reads a 16-bit integer as unsigned, in the buffer's byte order.
	 *
	 * @param b the buffer
	 * @param at the absolute position
	 * @return the value, from 0 to 65535
	 */
	public static int u16(ByteBuffer b, int at) {
		return Short.toUnsignedInt(b.getShort(at));
	}

	/**
	 * Reads a 32-bit integer as unsigned, in the buffer's byte order.
	 *
	 * @param b the buffer
	 * @param at the absolute position
	 * @return the value, from 0 to 2^32 - 1
	 */
	public static long u32(ByteBuffer b, int at) {
		return Integer.toUnsignedLong(b.getInt(at));
	}

	/**
	 * Reads a four-character code in ISO-8859-1.
	 *
	 * @param b the buffer
	 * @param at the absolute position
	 * @return the four characters
	 */
	public static String fourCc(ByteBuffer b, int at) {
		byte[] code = new byte[FOUR_CC];
		b.get(at, code);
		return new String(code, StandardCharsets.ISO_8859_1);
	}

	/**
	 * Reads an ID3v2 syncsafe integer: four bytes of seven bits each, most significant first.
	 *
	 * @param b the buffer
	 * @param at the absolute position
	 * @return the 28-bit value
	 */
	public static int syncsafe(ByteBuffer b, int at) {
		int value = 0;
		for (int i = 0; i < FOUR_CC; i++) {
			value = value << SYNCSAFE_BITS | b.get(at + i) & SYNCSAFE_MASK;
		}
		return value;
	}
}
