package com.duytv4.edge;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Logs method, URL, headers and (text/json) bodies of every request and its response.
 * Runs before Spring Security so rejected requests (401) are logged too.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestResponseLoggingFilter implements WebFilter {

	private static final int MAX_BODY_CHARS = 2000;

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		return Mono.deferContextual(ctx ->
				doFilter(exchange, chain, TraceIdResponseFilter.traceId(ctx).orElse("-")));
	}

	private Mono<Void> doFilter(ServerWebExchange exchange, WebFilterChain chain, String traceId) {
		long start = System.currentTimeMillis();
		ServerHttpRequest request = exchange.getRequest();
		String line = "[" + traceId + "] " + request.getMethod() + " " + request.getURI();
		AtomicReference<String> responseBody = new AtomicReference<>("");

		ServerHttpResponse response = new ServerHttpResponseDecorator(exchange.getResponse()) {
			@Override
			public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
				if (!isLoggable(getHeaders().getContentType())) {
					return super.writeWith(body);
				}
				return DataBufferUtils.join(Flux.from(body)).flatMap(buf -> {
					byte[] bytes = toBytes(buf);
					responseBody.set(new String(bytes, StandardCharsets.UTF_8));
					return super.writeWith(Mono.just(bufferFactory().wrap(bytes)));
				});
			}
		};

		Mono<Void> proceed;
		if (isLoggable(request.getHeaders().getContentType())) {
			proceed = DataBufferUtils.join(request.getBody())
					.map(this::toBytes)
					.defaultIfEmpty(new byte[0])
					.flatMap(bytes -> {
						log.info("--> {} headers={} body={}", line, headers(request.getHeaders()),
								truncate(new String(bytes, StandardCharsets.UTF_8)));
						ServerHttpRequest replay = new ServerHttpRequestDecorator(request) {
							@Override
							public Flux<DataBuffer> getBody() {
								return bytes.length == 0
										? Flux.empty()
										: Flux.just(exchange.getResponse().bufferFactory().wrap(bytes));
							}
						};
						return chain.filter(exchange.mutate().request(replay).response(response).build());
					});
		} else {
			log.info("--> {} headers={}", line, headers(request.getHeaders()));
			proceed = chain.filter(exchange.mutate().response(response).build());
		}

		return proceed.doFinally(signal -> log.info("<-- {} status={} {}ms headers={} body={}", line,
				exchange.getResponse().getStatusCode(), System.currentTimeMillis() - start,
				headers(exchange.getResponse().getHeaders()), truncate(responseBody.get())));
	}

	private boolean isLoggable(MediaType type) {
		if (type == null || MediaType.TEXT_EVENT_STREAM.isCompatibleWith(type)) {
			return false;
		}
		return type.getType().equals("text")
				|| type.getSubtype().contains("json")
				|| type.getSubtype().contains("xml")
				|| type.isCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED);
	}

	private byte[] toBytes(DataBuffer buf) {
		byte[] bytes = new byte[buf.readableByteCount()];
		buf.read(bytes);
		DataBufferUtils.release(buf);
		return bytes;
	}

	private HttpHeaders headers(HttpHeaders source) {
		HttpHeaders copy = new HttpHeaders();
		copy.addAll(source);
		if (copy.containsHeader(HttpHeaders.AUTHORIZATION)) {
			copy.set(HttpHeaders.AUTHORIZATION, "Bearer ***");
		}
		return copy;
	}

	private String truncate(String s) {
		return s.length() <= MAX_BODY_CHARS ? s : s.substring(0, MAX_BODY_CHARS) + "...(truncated)";
	}
}
