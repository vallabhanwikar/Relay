package com.relay.repository.api;

/** HTTP client abstractions the indexer recognises (ticket 12). */
public enum HttpClientKind {
    REST_TEMPLATE,
    WEB_CLIENT,
    FEIGN,
    HTTP_CLIENT,
    UNKNOWN
}
