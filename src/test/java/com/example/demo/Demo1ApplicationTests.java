package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

// Imports the container like every other integration test: without it this needs a developer's
// local Postgres from secrets.env, which does not exist on CI.
@SpringBootTest
@Import(PostgresTestContainer.class)
class Demo1ApplicationTests {

	@Test
	void contextLoads() {
	}

}
