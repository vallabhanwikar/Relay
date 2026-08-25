package com.relay.gateway.api;

/**
 * The single entry point for model work. Callers describe the task and the gateway decides the
 * tier, escalating only when the cheaper tier reports low confidence.
 */
public interface ModelGateway {

    ModelResponse execute(ModelRequest request);
}
