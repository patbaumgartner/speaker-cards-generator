package com.fortytwotalents.speakercardsgenerator.repository;

import com.fortytwotalents.speakercardsgenerator.model.Talk;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link Talk} entities. */
@Repository
public interface TalkRepository extends JpaRepository<Talk, Long> {

	@EntityGraph(attributePaths = "speakers")
	Optional<Talk> findWithSpeakersById(Long id);

	/**
	 * Talk banners list every speaker of the session. Without this graph the collection
	 * is lazy, and with {@code spring.jpa.open-in-view=false} it is already detached by
	 * the time the template iterates it.
	 */
	@EntityGraph(attributePaths = "speakers")
	List<Talk> findAllWithSpeakersBy();

}
