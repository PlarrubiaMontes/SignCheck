package dev.signcheck;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the SignCheck backend: a stateless REST API that validates advanced electronic
 * signatures (PAdES, XAdES, CAdES) with the EU DSS library.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling // Trusted Lists refresh (LOTL mode)
public class SignCheckApplication {

    public static void main(String[] args) {
        SpringApplication.run(SignCheckApplication.class, args);
    }
}
