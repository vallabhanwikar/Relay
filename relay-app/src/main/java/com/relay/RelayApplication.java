package com.relay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * Relay - an integration evidence engine.
 *
 * <p>When a third-party API changes, Relay detects the change, maps its blast radius across
 * consumer code, verifies (or generates) the fix, and emits an auditable Evidence Package.
 * The Evidence Package - not the patch - is the product.
 *
 * <p>This is a modular monolith. Every direct sub-package of {@code com.relay} is a Spring
 * Modulith application module with an explicitly declared dependency allowance. Boundaries are
 * enforced at build time by {@code ModularityTests}; there is no way to add a cross-module
 * dependency without editing the target module's {@code package-info.java} and saying why.
 */
@Modulithic(
        systemName = "Relay",
        sharedModules = "shared"
)
@SpringBootApplication
public class RelayApplication {

    public static void main(String[] args) {
        SpringApplication.run(RelayApplication.class, args);
    }
}
