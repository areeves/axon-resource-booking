package com.github.areeves.axon_resource_booking.resources;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@AutoConfigureMockMvc
class ResourceControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ResourceRepository resourceRepository;

	@Test
	void createsResourceAndProjectsItToDatabase() throws Exception {
		var result = mockMvc.perform(post("/resources")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Room A","description":"Training room","capacity":4,"location":"Floor 1"}
						"""))
				.andExpect(request().asyncStarted())
				.andReturn();

		mockMvc.perform(asyncDispatch(result))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/resources/.+")));

		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(resourceRepository.count()).isEqualTo(1));
	}
}