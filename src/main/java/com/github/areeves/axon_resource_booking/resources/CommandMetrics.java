package com.github.areeves.axon_resource_booking.resources;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class CommandMetrics {

	private final MeterRegistry meterRegistry;

	public CommandMetrics(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	public void increment(String command) {
		Counter.builder("axon.commands.total")
				.tag("command", command)
				.description("Number of commands accepted by the REST API")
				.register(meterRegistry)
				.increment();
	}
}