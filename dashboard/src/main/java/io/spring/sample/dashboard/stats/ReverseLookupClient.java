package io.spring.sample.dashboard.stats;

import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.MeterRegistry;
import io.spring.sample.dashboard.stats.support.ReverseLookupDescriptor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class ReverseLookupClient {

	private final WebClient client;

	private static final Log LOGGER = LogFactory.getLog(ReverseLookupClient.class);

	public ReverseLookupClient(WebClient.Builder builder, MeterRegistry registry) {
		this.client = builder.filter(rateLimitRemainingMetric(registry)).build();
	}

	public Mono<ReverseLookupDescriptor> freeReverseLookup(String ip) {
		return this.client.get().uri("http://localhost:8081/reverse-lookup/free/{ip}", ip)
				.retrieve().bodyToMono(ReverseLookupDescriptor.class).doOnNext((d) ->
						LOGGER.debug("Free reverse lookup service called."));
	}

	public Mono<ReverseLookupDescriptor> payingReverseLookup(String ip) {
		return this.client.get().uri("http://localhost:8081/reverse-lookup/costly/{ip}", ip)
				.retrieve().bodyToMono(ReverseLookupDescriptor.class).doOnNext((d) ->
						LOGGER.debug("Paid reverse lookup service called."));
	}

	private ExchangeFilterFunction rateLimitRemainingMetric(MeterRegistry registry) {
		AtomicInteger rateLimitRemaining = registry
				.gauge("reverselookup.ratelimit.remaining", new AtomicInteger(0));
		return (request, next) -> next.exchange(request)
				.doOnNext(response -> {
					String remaining = response.headers().asHttpHeaders()
							.getFirst("X-RateLimit-Remaining");
					if (StringUtils.hasText(remaining)) {
						rateLimitRemaining.set(Integer.parseInt(remaining));
					}
				});
	}
}
