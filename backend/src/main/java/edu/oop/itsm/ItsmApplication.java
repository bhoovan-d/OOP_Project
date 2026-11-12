package edu.oop.itsm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class ItsmApplication {

    public static void main(String[] args) {
        // Byte Buddy (Hibernate proxies) needs this to run on JDKs newer than it officially knows.
        System.setProperty("net.bytebuddy.experimental", "true");
        SpringApplication.run(ItsmApplication.class, args);
    }

    /** Injected everywhere "now" is needed, so time-based rules (SLA, escalation) are testable. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
