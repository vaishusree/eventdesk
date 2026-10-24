package com.eventdesk;

import org.springframework.boot.SpringApplication;

/**
 * Runs the app locally against a throwaway Postgres container, with no local
 * database setup needed: run this class's main method from IntelliJ.
 */
public class TestEventdeskApplication {

	public static void main(String[] args) {
		SpringApplication.from(EventdeskApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
