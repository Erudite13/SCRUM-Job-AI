package com.enterprise.ai.sprint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

@SpringBootApplication
@EnableR2dbcRepositories(basePackages = "com.enterprise.ai.sprint.repository")
public class SprintIntelligenceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SprintIntelligenceApplication.class, args);
    }
}
