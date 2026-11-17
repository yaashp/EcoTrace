package com.ecotrace.config;

import com.ecotrace.service.SeedService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Runs after start-up. Goes through the SeedService proxy so each step is transactional. */
@Component
public class StartupRunner implements CommandLineRunner {

    private final SeedService seed;

    public StartupRunner(SeedService seed) {
        this.seed = seed;
    }

    @Override
    public void run(String... args) {
        seed.bootstrapAdmin();
        if (seed.isDemoMode()) {
            seed.seedDemo();
        }
    }
}
