package com.relay.architecture;

import com.relay.RelayApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Ticket 3 - the module boundary test suite.
 *
 * <p>Section 11 of the plan lists scope creep as a HIGH severity risk and names the mitigation:
 * "ArchUnit tests physically stop module sprawl". This class is that mitigation. It runs on
 * every commit and fails the build when a module reaches into another module's internals.
 *
 * <p>These are pure static-analysis tests. No Spring context, no database, no containers -
 * which is why they can run as the first gate in CI and finish in seconds.
 */
class ModularityTests {

    private static final ApplicationModules MODULES = ApplicationModules.of(RelayApplication.class);

    @Test
    @DisplayName("module boundaries hold: no module reaches into another module's internals")
    void verifiesModularStructure() {
        // Fails on: access to a non-exposed package, a dependency not listed in the target
        // module's allowedDependencies, or a cycle between modules.
        MODULES.verify();
    }

    @Test
    @DisplayName("the nine application modules plus the shared kernel are all present")
    void allExpectedModulesArePresent() {
        var expected = java.util.List.of(
                "shared",
                "ingestion",
                "detection",
                "repository",
                "impact",
                "proof",
                "evidence",
                "repair",
                "gateway",
                "delivery"
        );

        var actual = MODULES.stream()
                .map(module -> module.getName())
                .sorted()
                .toList();

        org.assertj.core.api.Assertions.assertThat(actual)
                .as("A module appearing here that is not in the plan is scope creep with a "
                        + "package name. Adding one is a deliberate act that edits this test.")
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    /**
     * Writes the module canvas, PlantUML component diagrams and an AsciiDoc module report to
     * {@code target/spring-modulith-docs}. Phase 6 needs C4-style architecture diagrams for the
     * README and the blog series; generating them from the code means they cannot drift from it.
     */
    @Test
    @DisplayName("architecture documentation generates from the code itself")
    void writesDocumentationSnippets() {
        new Documenter(MODULES)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml()
                .writeModuleCanvases();
    }
}
