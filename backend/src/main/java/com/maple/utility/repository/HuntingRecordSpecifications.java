package com.maple.utility.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.maple.utility.entity.HuntingRecord;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

public final class HuntingRecordSpecifications {

	private HuntingRecordSpecifications() {
	}

	public static Specification<HuntingRecord> favoriteRecords(Long userId, LocalDate from, LocalDate to) {
		return (root, query, builder) -> {
			List<Predicate> predicates = datePredicates(root, builder, from, to);
			predicates.add(builder.equal(root.get("character").get("user").get("id"), userId));
			predicates.add(builder.isTrue(root.get("character").get("favorite")));
			return builder.and(predicates.toArray(Predicate[]::new));
		};
	}

	public static Specification<HuntingRecord> characterRecords(Long characterId, LocalDate from, LocalDate to) {
		return (root, query, builder) -> {
			List<Predicate> predicates = datePredicates(root, builder, from, to);
			predicates.add(builder.equal(root.get("character").get("id"), characterId));
			return builder.and(predicates.toArray(Predicate[]::new));
		};
	}

	private static List<Predicate> datePredicates(
			Root<HuntingRecord> root,
			CriteriaBuilder builder,
			LocalDate from,
			LocalDate to
	) {
		List<Predicate> predicates = new ArrayList<>();
		if (from != null) {
			predicates.add(builder.greaterThanOrEqualTo(root.get("recordDate"), from));
		}
		if (to != null) {
			predicates.add(builder.lessThanOrEqualTo(root.get("recordDate"), to));
		}
		return predicates;
	}
}
