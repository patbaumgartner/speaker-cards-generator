package com.fortytwotalents.speakercardsgenerator.repository;

import com.fortytwotalents.speakercardsgenerator.model.Session;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link Session} entities. */
@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {

	@EntityGraph(attributePaths = "speakers")
	Optional<Session> findWithSpeakersById(Long id);

	/**
	 * Session banners list every speaker. Without this graph the collection is lazy, and
	 * with {@code spring.jpa.open-in-view=false} it is already detached by the time the
	 * template iterates it.
	 */
	@EntityGraph(attributePaths = "speakers")
	List<Session> findAllWithSpeakersBy();

}
