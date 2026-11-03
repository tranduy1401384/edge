package com.duytv4.edge;

import io.micrometer.observation.Observation;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import io.micrometer.tracing.handler.TracingObservationHandler.TracingContext;
import java.util.Optional;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

/**
 * Returns the current traceId to the client in the X-Trace-Id response header.
 * WebFlux keeps the server observation in the Reactor context, not in a thread-local.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class TraceIdResponseFilter implements WebFilter {

	static final String HEADER = "X-Trace-Id";

	static Optional<String> traceId(ContextView ctx) {
		return ctx.<Observation>getOrEmpty(ObservationThreadLocalAccessor.KEY)
				.map(obs -> (TracingContext) obs.getContextView().get(TracingContext.class))
				.filter(tc -> tc != null && tc.getSpan() != null)
				.map(tc -> tc.getSpan().context().traceId());
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		return Mono.deferContextual(ctx -> {
			traceId(ctx).ifPresent(id -> exchange.getResponse().getHeaders().set(HEADER, id));
			return chain.filter(exchange);
		});
	}
}
