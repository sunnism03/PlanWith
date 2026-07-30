package com.JAI;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:context-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.data.redis.host=127.0.0.1",
		"spring.data.redis.port=16379",
		"spring.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
		"openai.api.key=context-test-key",
		"openai.api.url=http://127.0.0.1:18080/v1/responses"
})
class ToDoWithMeApplicationTests {

	@Test
	void contextLoads() {
	}

}
