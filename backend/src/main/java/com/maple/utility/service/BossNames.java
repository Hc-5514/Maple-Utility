package com.maple.utility.service;

import java.util.Map;

/** 넥슨 응답의 보스 이름을 boss_master 표기로 맞추는 별칭 처리. */
final class BossNames {

	// 공백을 제거한 넥슨 표기 → boss_master.boss_name
	private static final Map<String, String> ALIASES = Map.of("가디언엔젤슬라임", "가엔슬");

	private BossNames() {
	}

	static String canonical(String name) {
		if (name == null) {
			return null;
		}
		return ALIASES.getOrDefault(name.replaceAll("\\s+", ""), name);
	}
}
