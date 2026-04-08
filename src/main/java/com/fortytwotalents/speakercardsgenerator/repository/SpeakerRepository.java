package com.fortytwotalents.speakercardsgenerator.repository;

import com.fortytwotalents.speakercardsgenerator.model.Speaker;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link Speaker} entities. */
@Repository
public interface SpeakerRepository extends JpaRepository<Speaker, UUID> {

	@EntityGraph(attributePaths = "talks")
	Optional<Speaker> findWithTalksById(UUID id);

	@EntityGraph(attributePaths = "talks")
	List<Speaker> findAllWithTalksBy();

}
