package com.relay.shared;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One checkable claim about a change (design v3, "Core concepts").
 *
 * <p>Both entry paths reduce to obligations: an upstream spec change yields schema-compatibility
 * obligations, a pull request yields contract and runtime-exception obligations. The proof module
 * discharges them without knowing which path produced them, which is what keeps it independent
 * of where a change came from.
 *
 * @param formalBody  the claim in its verifier's language: SMT-LIB, JML, a TLA+ operator name,
 *                    or a reference to the schemas being compared
 * @param claim       the same claim in one plain-English sentence, as shown in the PR comment
 * @param assumptions conditions the claim is checked under ("nesting depth &lt;= 6", "loop bound 8")
 * @param scope       the code or payload locations the claim covers, from the blast radius
 * @param approvedBy  the human who approved an LLM-proposed spec; required for HUMAN_APPROVED
 */
public record Obligation(
        Ids.ObligationId id,
        ObligationKind kind,
        String claim,
        String formalBody,
        List<String> assumptions,
        Set<String> scope,
        SpecProvenance provenance,
        Optional<String> approvedBy
) {

    public Obligation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(formalBody, "formalBody");
        Objects.requireNonNull(provenance, "provenance");
        assumptions = List.copyOf(assumptions);
        scope = Set.copyOf(scope);
        approvedBy = Objects.requireNonNullElse(approvedBy, Optional.empty());
        if (provenance == SpecProvenance.HUMAN_APPROVED && approvedBy.isEmpty()) {
            throw new IllegalArgumentException("a HUMAN_APPROVED obligation must name its approver");
        }
        if (provenance != SpecProvenance.HUMAN_APPROVED && approvedBy.isPresent()) {
            throw new IllegalArgumentException("only a HUMAN_APPROVED obligation has an approver");
        }
    }

    /** Whether a verdict on this obligation may count as evidence at all. */
    public boolean mayCarryProof() {
        return provenance.mayCarryProof();
    }

    /** The approved form of an LLM-proposed obligation. The formal body does not change. */
    public Obligation approve(String approver) {
        if (provenance != SpecProvenance.LLM_PROPOSED) {
            throw new IllegalStateException("only an LLM-proposed obligation needs approval, this one is " + provenance);
        }
        return new Obligation(id, kind, claim, formalBody, assumptions, scope,
                SpecProvenance.HUMAN_APPROVED, Optional.of(approver));
    }
}
