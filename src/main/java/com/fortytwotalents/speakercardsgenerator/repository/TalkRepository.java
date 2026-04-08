package com.fortytwotalents.speakercardsgenerator.repository;

import com.fortytwotalents.speakercardsgenerator.model.Talk;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link Talk} entities. */
@Repository
public interface TalkRepository extends JpaRepository<Talk, Long> {

	@EntityGraph(attributePaths = "speakers")
	Optional<Talk> findWithSpeakersById(Long id);

}
