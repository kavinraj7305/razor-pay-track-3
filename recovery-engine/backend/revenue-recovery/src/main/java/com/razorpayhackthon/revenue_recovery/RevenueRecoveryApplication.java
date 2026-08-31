package com.razorpayhackthon.revenue_recovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class RevenueRecoveryApplication {

	public static void main(String[] args) {
		SpringApplication.run(RevenueRecoveryApplication.class, args);
	}

}
