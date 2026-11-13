package com.om.AnomalyDetectionApplication;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AnomalyDetectionApplicationTests {

	@Autowired
	private ApplicationContext context;

	@Test
	void contextLoads() {
	}

	@Test
	void noDefaultInMemoryUserIsCreated() {
		assertThat(context.getBeanNamesForType(UserDetailsService.class)).isEmpty();
	}

}
