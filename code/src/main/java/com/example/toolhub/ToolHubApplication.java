package com.example.toolhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class ToolHubApplication {

	public static void main(String[] args) {
		SpringApplication.run(ToolHubApplication.class, args);
	}

}
