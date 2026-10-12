package com.github.areeves.axon_resource_booking.resources;

import java.util.List;
import java.util.function.BiFunction;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.springframework.stereotype.Component;

@Component
public class CommandMetricsInterceptor implements MessageDispatchInterceptor<CommandMessage<?>> {

	private final CommandMetrics commandMetrics;

	public CommandMetricsInterceptor(CommandMetrics commandMetrics) {
		this.commandMetrics = commandMetrics;
	}

	@Override
	public BiFunction<Integer, CommandMessage<?>, CommandMessage<?>> handle(
			List<? extends CommandMessage<?>> messages) {
		messages.forEach(message -> commandMetrics.increment(message.getPayloadType()));
		return (index, message) -> message;
	}
}
