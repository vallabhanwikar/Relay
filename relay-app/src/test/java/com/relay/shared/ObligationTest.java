package com.relay.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Design v3, invariant 4: a model-written spec carries no proof until a named human approves it.
 * The proof module enforces this at run time; these tests pin the vocabulary it relies on.
 */
class ObligationTest {

    private static Obligation proposed() {
        return new Obligation(Ids.ObligationId.random(), ObligationKind.JML_CONTRACT,
                "calculateDiscount never returns more than the price",
                "ensures \\result <= price;", List.of(), Set.of("Pricing.calculateDiscount"),
                SpecProvenance.LLM_PROPOSED, Optional.empty());
    }

    @Test
    @DisplayName("an LLM-proposed obligation cannot carry proof")
    void proposedCannotCarryProof() {
        assertFalse(proposed().mayCarryProof());
    }

    @Test
    @DisplayName("approval names the approver, keeps the formal body, and unlocks proof")
    void approvalUnlocksProof() {
        Obligation original = proposed();
        Obligation approved = original.approve("vallabh");

        assertTrue(approved.mayCarryProof());
        assertEquals(SpecProvenance.HUMAN_APPROVED, approved.provenance());
        assertEquals(Optional.of("vallabh"), approved.approvedBy());
        assertEquals(original.formalBody(), approved.formalBody());
    }

    @Test
    @DisplayName("HUMAN_APPROVED without an approver is unrepresentable")
    void approvalNeedsAnApprover() {
        assertThrows(IllegalArgumentException.class, () -> new Obligation(Ids.ObligationId.random(),
                ObligationKind.JML_CONTRACT, "c", "f", List.of(), Set.of(),
                SpecProvenance.HUMAN_APPROVED, Optional.empty()));
        assertThrows(IllegalStateException.class, () -> proposed().approve("a").approve("b"));
    }

    @Test
    @DisplayName("only PROVEN is a pass; UNPROVEN and ERROR never are")
    void onlyProvenPasses() {
        assertTrue(Verdict.PROVEN.isPass());
        assertFalse(Verdict.UNPROVEN.isPass());
        assertFalse(Verdict.ERROR.isPass());
        assertFalse(Verdict.COUNTEREXAMPLE.isPass());
        assertTrue(EvidenceLevel.E6B_FORMAL_PROOF.outranks(EvidenceLevel.E6A_BOUNDED_FORMAL));
    }
}
