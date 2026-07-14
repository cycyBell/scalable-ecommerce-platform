package com.rtxnano.ecommerce.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootApplication
// @EnableMethodSecurity turns on support for annotations like
// @PreAuthorize directly on controller methods. Without this,
// @PreAuthorize annotations would simply be ignored — present in the
// code, but never actually enforced. This is a deliberate opt-in step
// in modern Spring Security (replacing the older
// @EnableGlobalMethodSecurity from earlier tutorial versions).
@EnableMethodSecurity
public class UserServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(UserServiceApplication.class, args);
	}

}
