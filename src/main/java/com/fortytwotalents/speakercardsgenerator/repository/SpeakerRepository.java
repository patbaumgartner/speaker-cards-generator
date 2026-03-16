package com.fortytwotalents.speakercardsgenerator.repository;

import com.fortytwotalents.speakercardsgenerator.model.Speaker;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link Speaker} entities. */
@Repository
public interface SpeakerRepository extends JpaRepository<Speaker, UUID> {

}
