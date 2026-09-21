package com.github.areeves.axon_resource_booking;

import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.spring.messaging.unitofwork.SpringTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.transaction.PlatformTransactionManager;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;

@Configuration
@EntityScan(basePackages = {
		"com.github.areeves.axon_resource_booking.resources",
			"org.axonframework.eventsourcing.eventstore.jpa",
			"org.axonframework.eventhandling.tokenstore.jpa"
})
public class AxonConfiguration {

	@Bean(name = "eventSerializer")
	Serializer eventSerializer(ObjectMapper objectMapper) {
		return JacksonSerializer.builder().objectMapper(objectMapper).build();
	}

	@Bean
	EntityManagerProvider entityManagerProvider(EntityManager entityManager) {
		return new SimpleEntityManagerProvider(entityManager);
	}

	@Bean
	TransactionManager axonTransactionManager(PlatformTransactionManager transactionManager) {
		return new SpringTransactionManager(transactionManager);
	}

	@Bean
	EventStorageEngine eventStorageEngine(EntityManagerProvider entityManagerProvider,
			TransactionManager transactionManager) {
		return JpaEventStorageEngine.builder()
				.entityManagerProvider(entityManagerProvider)
				.transactionManager(transactionManager)
				.build();
	}
}