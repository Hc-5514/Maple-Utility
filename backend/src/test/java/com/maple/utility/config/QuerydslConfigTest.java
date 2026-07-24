package com.maple.utility.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import com.querydsl.jpa.impl.JPAQueryFactory;

class QuerydslConfigTest {

	@Test
	void jpaQueryFactory() {
		QuerydslConfig config = new QuerydslConfig();
		EntityManager entityManager = mock(EntityManager.class);

		JPAQueryFactory queryFactory = config.jpaQueryFactory(entityManager);

		assertThat(queryFactory).isNotNull();
	}
}
