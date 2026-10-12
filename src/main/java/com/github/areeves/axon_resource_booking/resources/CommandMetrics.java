package com.github.areeves.axon_resource_booking.resources;

import java.util.Locale;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class CommandMetrics {

	private final MeterRegistry meterRegistry;

	public CommandMetrics(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	public void increment(Class<?> commandType) {
		String commandName = commandType.getSimpleName().replaceFirst("Command$", "")
				.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
				.toLowerCase(Locale.ROOT);
		Counter.builder("axon.commands.total")
				.tag("command", commandName)
				.description("Number of commands dispatched through Axon")
				.register(meterRegistry)
				.increment();
	}
}