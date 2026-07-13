package com.maple.utility;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"JWT_SECRET=test-jwt-secret-for-context-load",
		"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
})
class MapleUtilityApplicationTests {

	@Test
	void contextLoads() {
	}

}
