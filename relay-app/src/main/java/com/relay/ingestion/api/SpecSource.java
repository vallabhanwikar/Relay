package com.relay.ingestion.api;

/** How a spec version arrived. Recorded in evidence so a reviewer can judge its provenance. */
public enum SpecSource {
    UPLOAD,
    URL_POLL,
    GIT_WATCH
}
