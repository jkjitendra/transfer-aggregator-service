package com.arcube.transferaggregator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TransferAggregatorApplication {

	public static void main(String[] args) {
		SpringApplication.run(TransferAggregatorApplication.class, args);
	}

}

