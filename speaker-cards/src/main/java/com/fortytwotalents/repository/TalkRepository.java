package com.fortytwotalents.repository;

import com.fortytwotalents.model.Talk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link Talk} entities. */
@Repository
public interface TalkRepository extends JpaRepository<Talk, Long> {}
