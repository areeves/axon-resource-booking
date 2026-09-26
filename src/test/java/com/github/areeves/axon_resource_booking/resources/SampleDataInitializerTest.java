package com.github.areeves.axon_resource_booking.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;

import org.axonframework.eventsourcing.eventstore.EventStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"app.sample-data.enabled=true",
		"spring.datasource.url=jdbc:h2:mem:sample_data_seeder;DB_CLOSE_DELAY=-1"
})
class SampleDataInitializerTest {
	private static final UUID ATLAS_RESOURCE_ID = UUID.fromString("a8bd0a34-27bd-4d52-9ac1-43c8f0a10001");
	private static final UUID ORION_RESOURCE_ID = UUID.fromString("a8bd0a34-27bd-4d52-9ac1-43c8f0a10002");

	@Autowired
	private EventStore eventStore;

	@Autowired
	private ResourceRepository resourceRepository;

	@Autowired
	private ReservationRepository reservationRepository;

	@Autowired
	private SampleDataInitializer initializer;

	@Test
	void seedsResourcesAndReservationsOnce() throws Exception {
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			assertThat(resourceRepository.count()).isEqualTo(2);
			assertThat(reservationRepository.count()).isEqualTo(3);
		});

		initializer.run(new DefaultApplicationArguments(new String[0]));

		assertThat(eventCount(ATLAS_RESOURCE_ID)).isEqualTo(3);
		assertThat(eventCount(ORION_RESOURCE_ID)).isEqualTo(2);
	}

	private long eventCount(UUID aggregateId) {
		return eventStore.readEvents(aggregateId.toString()).asStream().count();
	}
}