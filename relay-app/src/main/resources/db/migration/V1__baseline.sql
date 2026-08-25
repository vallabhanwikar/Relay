-- V1 - baseline.
--
-- Deliberately empty of domain tables. Each module brings its own migration when its phase
-- arrives, so that a table's existence always traces to a ticket that needed it:
--
--   V2  ingestion    external_api, api_version, api_endpoint       (ticket 6)
--   V3  detection    change_set, change_event                      (ticket 7-8)
--   V4  repository   indexed_repository, symbol, call_site, edge   (ticket 11-13)
--   V5  impact       impact_analysis, affected_site                (ticket 14)
--   V6  proof        verification_run, check_run                   (ticket 16-17)
--   V7  evidence     invariant, check, verdict, evidence_package   (ticket 19)
--
-- pgvector is created here because similarity search over change patterns is cross-cutting:
-- "have we seen this change before" is asked by detection and answered against evidence.

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

COMMENT ON EXTENSION vector IS
    'Similarity search over change patterns - one database, no separate vector store.';
