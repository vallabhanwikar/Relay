package com.relay.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Ticket 3 - the rules Spring Modulith does not express on its own.
 *
 * <p>Modulith enforces module boundaries. These rules enforce the decisions that make Relay's
 * evidence trustworthy, each one guarding a specific way the project could quietly go wrong.
 */
class ArchitectureRulesTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.relay");

    @Test
    @DisplayName("no cycles between application modules")
    void noCyclesBetweenModules() {
        ArchRule rule = slices()
                .matching("com.relay.(*)..")
                .should().beFreeOfCycles();

        rule.check(CLASSES);
    }

    @Test
    @DisplayName("internals stay internal: no cross-module access to an internal package")
    void internalPackagesAreNotAccessedAcrossModules() {
        ArchRule rule = layeredArchitecture()
                .consideringOnlyDependenciesInAnyPackage("com.relay..")
                .layer("Shared").definedBy("com.relay.shared..")
                .layer("Ingestion").definedBy("com.relay.ingestion..")
                .layer("Detection").definedBy("com.relay.detection..")
                .layer("RepositoryIntel").definedBy("com.relay.repository..")
                .layer("Impact").definedBy("com.relay.impact..")
                .layer("Proof").definedBy("com.relay.proof..")
                .layer("Evidence").definedBy("com.relay.evidence..")
                .layer("Repair").definedBy("com.relay.repair..")
                .layer("Gateway").definedBy("com.relay.gateway..")
                .layer("Delivery").definedBy("com.relay.delivery..")

                .whereLayer("Delivery").mayNotBeAccessedByAnyLayer()
                .whereLayer("Repair").mayNotBeAccessedByAnyLayer()
                .whereLayer("Evidence").mayOnlyBeAccessedByLayers("Delivery")
                .whereLayer("Proof").mayOnlyBeAccessedByLayers("Evidence", "Repair")
                .whereLayer("Impact").mayOnlyBeAccessedByLayers("Proof", "Repair")
                .whereLayer("Detection").mayOnlyBeAccessedByLayers("Impact")
                .whereLayer("RepositoryIntel").mayOnlyBeAccessedByLayers("Impact", "Repair")
                .whereLayer("Ingestion").mayOnlyBeAccessedByLayers("Detection")
                .whereLayer("Gateway").mayOnlyBeAccessedByLayers("Repair");

        rule.check(CLASSES);
    }

    /**
     * The proof engine must never learn where a patch came from.
     *
     * <p>Section 3 of the plan positions coding agents as Relay's suppliers rather than its
     * rivals, and the Week-12 milestone demo verifies a patch a human wrote. Both claims are
     * only true while this rule passes. If proof could import repair, "we verify patches from
     * any source" would be marketing rather than architecture.
     */
    @Test
    @DisplayName("the proof engine is neutral about where a patch came from")
    void proofDoesNotDependOnRepair() {
        ArchRule rule = com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
                .that().resideInAPackage("com.relay.proof..")
                .should().dependOnClassesThat().resideInAPackage("com.relay.repair..")
                .because("the proof engine verifies patches from humans, Relay's agent and "
                        + "external agents identically; a dependency here would make that claim false");

        rule.check(CLASSES);
    }

    /**
     * Nothing outside the gateway may call a model.
     *
     * <p>The gateway owns tier routing and per-task token telemetry. A model call made anywhere
     * else is invisible to that telemetry, which would silently corrupt the deterministic /
     * local / frontier work-mix ratio the plan wants to report.
     */
    @Test
    @DisplayName("only the gateway module talks to models")
    void onlyGatewayDependsOnSpringAi() {
        ArchRule rule = com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
                .that().resideOutsideOfPackage("com.relay.gateway..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.ai..")
                .because("every model call must be routed and metered by the gateway, or the "
                        + "measured work-mix ratio stops being measured");

        rule.check(CLASSES);
    }

    /**
     * The shared kernel is a vocabulary, not a dumping ground. It is an OPEN module visible to
     * everyone, so anything with behaviour placed here becomes a dependency everyone silently
     * acquires.
     */
    @Test
    @DisplayName("the shared kernel holds no Spring beans")
    void sharedKernelHoldsNoBeans() {
        ArchRule rule = com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
                .that().resideInAPackage("com.relay.shared..")
                .should().beAnnotatedWith("org.springframework.stereotype.Service")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Component")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Repository")
                .because("shared is a vocabulary of value types; behaviour belongs to a module "
                        + "that owns it");

        rule.check(CLASSES);
    }

    /**
     * Records leaving a module boundary must be immutable all the way down. An Evidence Package
     * whose collections could be mutated after freezing would invalidate its own content hash.
     */
    @Test
    @DisplayName("module API types carry only final fields")
    void moduleApiFieldsAreFinal() {
        ArchRule rule = com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields()
                .that().areDeclaredInClassesThat().resideInAPackage("com.relay..api..")
                .should().beFinal()
                .because("module APIs are value types crossing a boundary; a mutable one lets a "
                        + "consumer change an object after it was recorded as evidence");

        rule.check(CLASSES);
    }
}
