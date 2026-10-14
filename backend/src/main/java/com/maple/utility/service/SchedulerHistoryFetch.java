package com.maple.utility.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import com.maple.utility.security.NexonOpenApiClient;
import com.maple.utility.security.NexonRequestMode;
import com.maple.utility.security.NexonSchedulerResponse;

/** 동일 실행에서 날짜별 응답·실패 공유. 키·원본의 장기 캐시 보관 제외. */
public final class SchedulerHistoryFetch {
	private record Key(Long userId, String ocid, LocalDate date) { }
	private record Result(NexonSchedulerResponse response, RuntimeException failure) { }
	private final Map<Key, Result> responses = new HashMap<>();
	private final NexonOpenApiClient client;
	public SchedulerHistoryFetch(NexonOpenApiClient client) { this.client = client; }
	public NexonSchedulerResponse get(Long userId, String ocid, LocalDate date, NexonRequestMode mode) {
		var result = responses.computeIfAbsent(new Key(userId, ocid, date), key -> {
			try { return new Result(client.getCharacterSchedulerForDateStrict(userId, ocid, date, mode), null); }
			catch (RuntimeException failure) { return new Result(null, failure); }
		});
		if (result.failure() != null) { throw result.failure(); }
		return result.response();
	}
}
