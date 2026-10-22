package io.github.leonardodvf.deliverytracker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DeliveryTrackerApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
