package com.maple.utility.entity;

import java.util.List;

/** 선택 상자 아이템의 상세 획득 아이템 옵션. 옵션이 없는 아이템은 빈 목록. */
public final class BossItemDetailOptions {

	private static final List<String> RING_BOX = List.of("리스트레인트 링 Lv4", "컨티뉴어스링 Lv4", "기타");
	private static final List<String> CHAOS_BLACK_ACCESSORY_BOX = List.of(
			"루즈 컨트롤 머신 마크", "마력이 깃든 안대", "몽환의 벨트", "저주받은 마도서 선택 상자",
			"거대한 공포", "커맨더 포스 이어링", "고통의 근원");

	private BossItemDetailOptions() {
	}

	public static List<String> optionsFor(String itemName) {
		if (itemName == null) {
			return List.of();
		}
		if (itemName.endsWith("보스 반지 상자")) {
			return RING_BOX;
		}
		if (itemName.equals("혼돈의 칠흑 장신구 상자")) {
			return CHAOS_BLACK_ACCESSORY_BOX;
		}
		return List.of();
	}
}
