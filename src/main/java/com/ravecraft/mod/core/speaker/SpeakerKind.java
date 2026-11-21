package com.ravecraft.mod.core.speaker;

/** The kinds of speaker, each with its own acoustic profile. */
public enum SpeakerKind {
	/** The small omnidirectional speaker. */
	PORTABLE("portable"),
	/** The large directional party speaker. */
	PARTY("party");

	private final String id;

	SpeakerKind(String id) {
		this.id = id;
	}

	/** The short name used in the settings file and in translation keys. */
	public String id() {
		return id;
	}
}
