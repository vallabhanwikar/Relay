/**
 * Evidence - the Evidence Graph, package rendering, content hashing.
 *
 * <p>The core data model and, per the plan's inversion, the actual product. Every claim Relay
 * makes is a node with edges to the machine-checkable facts supporting it:
 * Invariant -> Check -> Run -> Verdict, hung off ChangeEvent / Impact / Patch / Approval.
 *
 * <p>An Evidence Package is a frozen, content-hashed export of the subgraph for one change:
 * "what changed, what it touched, what we checked, what passed, who approved." Rendered as
 * human-readable HTML/PDF and machine-readable JSON. Content hashing is what makes it an
 * auditable artefact rather than a report - the same object later serves SR 11-7-style
 * compliance review if Gate B goes toward regulated industries.
 *
 * <p>{@code EvidencePackage} is the aggregate root of this module (Appendix B), not an
 * afterthought of verification.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Evidence Graph",
        allowedDependencies = {"proof::api"}
)
package com.relay.evidence;
