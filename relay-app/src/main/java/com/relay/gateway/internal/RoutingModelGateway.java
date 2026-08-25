package com.relay.gateway.internal;

import org.springframework.stereotype.Service;

/**
 * TODO(Phase 4): Spring AI behind this class - Ollama for dev, an OpenAI-compatible vLLM
 * endpoint for prod/demo, frontier optional and customer-keyed.
 *
 * <p>Model portability is the reason Spring AI is here rather than a raw HTTP client: the
 * interview point is that the proof engine is indifferent to which model wrote the patch, and
 * that only holds if swapping the model is a configuration change.
 */
@Service
class RoutingModelGateway {
}
