package com.fortytwotalents.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fortytwotalents.model.Speaker;

/**
 * Spring Data JPA repository for {@link Speaker} entities.
 */
@Repository
public interface SpeakerRepository extends JpaRepository<Speaker, UUID> {
}
