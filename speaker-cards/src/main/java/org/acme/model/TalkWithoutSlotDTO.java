package org.acme.model;

import java.util.List;

public class TalkWithoutSlotDTO {
    public Long id;
    public String title;
    public List<String> speakers;

    public TalkWithoutSlotDTO() {
    }

    public TalkWithoutSlotDTO(Long id, String title, List<String> speakers) {
        this.id = id;
        this.title = title;
        this.speakers = speakers;
    }
}

