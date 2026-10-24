package com.eventdesk;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class EventdeskApplicationTests {

	/**
	 * Starting the app runs every Flyway migration against a real Postgres,
	 * then Hibernate validates that each entity matches the tables
	 * (ddl-auto=validate). A wrong column name or type fails this test.
	 */
	@Test
	void contextLoads() {
	}

}
