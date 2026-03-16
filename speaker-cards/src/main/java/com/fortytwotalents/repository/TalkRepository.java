package com.fortytwotalents.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fortytwotalents.model.Talk;

/**
 * Spring Data JPA repository for {@link Talk} entities.
 */
@Repository
public interface TalkRepository extends JpaRepository<Talk, Long> {
}
