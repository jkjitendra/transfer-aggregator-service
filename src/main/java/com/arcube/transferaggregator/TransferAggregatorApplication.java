package com.arcube.transferaggregator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TransferAggregatorApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(TransferAggregatorApplication.class);
        application.addInitializers(context -> validateRuntimeProfile(context.getEnvironment()));
        application.run(args);
    }

    static void validateRuntimeProfile(Environment environment) {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length != 1 || !("local".equals(profiles[0]) || "prod".equals(profiles[0]))) {
            throw new IllegalStateException("Set SPRING_PROFILES_ACTIVE to exactly local or prod");
        }
    }

}

