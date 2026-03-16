package com.fortytwotalents.model;

import java.util.List;

/**
 * Lightweight DTO representing a talk without slot / scheduling information.
 *
 * <p>
 * Used in API responses where full scheduling details are not required.
 */
public class TalkWithoutSlotDTO {

	public Long id;

	public String title;

	public List<String> speakers;

	/** Default constructor for Jackson deserialization. */
	public TalkWithoutSlotDTO() {
	}

	/**
	 * Convenience constructor.
	 * @param id talk identifier
	 * @param title session title
	 * @param speakers list of speaker display names
	 */
	public TalkWithoutSlotDTO(Long id, String title, List<String> speakers) {
		this.id = id;
		this.title = title;
		this.speakers = speakers;
	}

}
