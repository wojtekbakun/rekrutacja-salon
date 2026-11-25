package com.example.dealership.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    // Single source of "today" for business rules, replaceable with a fixed clock in tests.
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
