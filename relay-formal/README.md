# relay-formal

Formal verifiers as plain Java libraries. No Spring, no database, no network. A proof is
reproducible from its inputs with this jar and Z3.

The first verifier is the **Z3 schema-compatibility prover**. It answers one question for one
consumer: *can the new version of an API contract produce something this consumer does not
accept?* The answer is one of the following:

| Verdict | Meaning |
|---|---|
| `PROVEN` (E6b) | Z3 showed that no such payload exists, under the listed assumptions |
| `COUNTEREXAMPLE` | A concrete JSON payload, plus the consumer expectations it breaks |
| `UNPROVEN` | Z3 could not decide (timeout), or the question was vacuous |
| `ERROR` | The check could not run (malformed schema, solver fault) |

## Try it

```bash
mvn -pl relay-formal -am package
java -cp "relay-formal/target/classes:$(mvn -q -pl relay-formal dependency:build-classpath -Dmdep.outputFile=/dev/stdout)" \
  com.relay.formal.cli.SchemaCompatCli \
  --old relay-formal/examples/payments-api-v1.json \
  --new relay-formal/examples/payments-api-v2.json \
  --operation "GET /payments/{id}" --uses status,amount.value
```

Payments v2 adds a status value, drops `legacyRef` and adds two optional fields. For a consumer
that reads `status` and `amount.value`, it prints something like this (the exact witness values
can vary between Z3 versions):

```json
{
  "verdict": "COUNTEREXAMPLE",
  "witness": {"amount": {"currency": "EUR", "value": 0}, "id": "", "status": "PARTIALLY_REFUNDED"},
  "violations": [{"path": "status", "expectation": "must be one of [PAID, FAILED]"}],
  "exact": true
}
```

Add `--tolerate status=UNKNOWN_ENUM_SAFE` (the consumer's `switch` has a default branch) and
the same change is `PROVEN`. The removed field and the added fields never come up, because this
consumer does not read them.

## How it decides

The checker encodes an existential question and asks Z3 whether it is satisfiable:

```
RESPONSE:  exists p.  ValidNew(p)  and  not Expects(p)
REQUEST:   exists p.  Sends(p)     and  not AcceptsNew(p)
```

`Expects` is the old contract restricted to the fields the consumer uses (`Usage`). `Sends` is
the old contract restricted to the fields the consumer sends. UNSAT means `PROVEN`. SAT gives a
model, which is decoded into the witness.

A payload is described path by path. Each path has variables for whether it is present, its
JSON kind, and one value per kind. Well-formedness axioms tie children to their parents. Each
schema requirement becomes one named clause, so a counterexample can say *which* expectation it
breaks.

### Why the proofs are sound

One rule covers every encoding shortcut: **the producing side may be over-approximated, the
accepting side may not.** If A' ⊇ A and B' ⊆ B, then UNSAT of `A' ∧ ¬B'` implies UNSAT of
`A ∧ ¬B`.

| Construct | Encoding | Why it is safe |
|---|---|---|
| Fields outside the usage scope | Unconstrained | Over-approximates the producer; defines the consumer's scope |
| `pattern`, `format` | One uninterpreted predicate per distinct pattern or format, shared by both sides | Identical requirements cancel. A requirement only the consumer has yields a counterexample marked inexact for replay |
| Arrays | One representative element plus a length | Constraints are per element, so a violating element can be copied to every position |
| Array length | Bounded by the largest length constant + 2 | Lengths appear only in comparisons with those constants |
| `oneOf` | Treated as `anyOf` | Over-approximates the producer. On the consumer side it means "handles every variant" |
| `multipleOf` | `value · D` is an integer `t` with `t mod (step · D) = 0`, where `D` is one shared power of ten | Exact. Stays in linear integer arithmetic: a per-step encoding timed out on `0.1` vs `0.01` |
| Recursive `$ref` | Expanded to a depth bound, then left unconstrained | Reported as "not checked", never silently |

There are two further guards:

- **Vacuity.** If the producing side admits no payload at all (say `minItems: 2, maxItems: 1`),
  the result is `UNPROVEN: vacuous`, never `PROVEN`. A request usage that omits a field the old
  contract required is completed automatically. Otherwise the producing side would contradict
  itself.
- **Witness completeness.** On the producer's side, the encoding also covers what the producer
  *requires* beyond the consumer's scope. A witness is therefore a whole valid document, not
  just the fields being read.

### How this is tested

- `ResponseCompatibilityTest` and `RequestCompatibilityTest`: one test per row of the change
  taxonomy, run against the real solver.
- `ConcreteValidator`: a plain JSON Schema validator that shares no code with the encoder.
  Every exact witness has to pass it under the producing schema.
- `SoundnessFuzzTest`: differential testing against brute force. Random schema pairs go through
  the prover. Every `PROVEN` is then attacked with 3,000 sampled payloads, and every exact
  counterexample is re-validated on both sides.
  - Breaking the encoder on purpose (dropping the "must not be null" clause) makes this test
    fail with `UNSOUND PROOF`.
  - For a longer hunt: `mvn -pl relay-formal test -Dtest=SoundnessFuzzTest -Drelay.fuzz.seed=7 -Drelay.fuzz.pairs=5000`.

## Not modelled yet

`not`, `uniqueItems`, `minProperties`/`maxProperties`, `patternProperties`, `if`/`then`/`else`,
and external `$ref`s. These keywords are reported as assumptions on every verdict rather than
silently ignored. YAML input arrives with the OpenAPI ingestion work in `relay-app`. The CLI
reads JSON only.
