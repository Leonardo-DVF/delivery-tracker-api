package io.github.leonardodvf.deliverytracker;

import org.springframework.boot.SpringApplication;

public class TestDeliveryTrackerApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(DeliveryTrackerApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
