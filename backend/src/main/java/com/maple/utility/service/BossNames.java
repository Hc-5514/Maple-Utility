package com.maple.utility.service;

import java.util.Map;

/** 넥슨 응답의 보스 이름을 boss_master 이름과 맞추는 조회 키 처리(공백 무시, 별칭). */
public final class BossNames {

	// 공백을 제거한 넥슨 표기 → 공백을 제거한 boss_master.boss_name
	private static final Map<String, String> ALIASES = Map.of("가디언엔젤슬라임", "가엔슬");

	private BossNames() {
	}

	/** 공백을 모두 제거하고 별칭을 적용한 조회 키. boss_master 쪽도 공백을 제거해 비교한다. */
	public static String lookupKey(String name) {
		if (name == null) {
			return null;
		}
		String key = name.replaceAll("\\s+", "");
		return ALIASES.getOrDefault(key, key);
	}
}
