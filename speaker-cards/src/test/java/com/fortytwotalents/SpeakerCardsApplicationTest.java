package com.fortytwotalents;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Spring Boot application context smoke test.
 *
 * <p>Verifies that the application context starts up without errors using
 * an in-memory H2 database (no PostgreSQL required for tests).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class SpeakerCardsApplicationTest {

    /**
     * Verifies that the Spring application context loads successfully.
     */
    @Test
    void contextLoads() {
        // Context loading is verified by the @SpringBootTest annotation itself.
    }
}
