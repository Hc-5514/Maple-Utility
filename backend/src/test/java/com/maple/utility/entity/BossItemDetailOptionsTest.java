package com.maple.utility.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BossItemDetailOptionsTest {

	@Test
	void everyBossRingBoxHasTheSameThreeOptions() {
		for (String name : new String[] {"녹옥의 보스 반지 상자", "홍옥의 보스 반지 상자", "흑옥의 보스 반지 상자", "백옥의 보스 반지 상자", "생명의 보스 반지 상자"}) {
			assertThat(BossItemDetailOptions.optionsFor(name))
					.containsExactly("리스트레인트 링 Lv4", "컨티뉴어스링 Lv4", "기타");
		}
	}

	@Test
	void chaosBlackAccessoryBoxHasSevenOptions() {
		assertThat(BossItemDetailOptions.optionsFor("혼돈의 칠흑 장신구 상자")).containsExactly(
				"루즈 컨트롤 머신 마크", "마력이 깃든 안대", "몽환의 벨트", "저주받은 마도서 선택 상자",
				"거대한 공포", "커맨더 포스 이어링", "고통의 근원");
	}

	@Test
	void otherItemsHaveNoOptions() {
		assertThat(BossItemDetailOptions.optionsFor("강렬한 힘의 결정")).isEmpty();
		assertThat(BossItemDetailOptions.optionsFor("앱솔랩스 무기 상자")).isEmpty();
		assertThat(BossItemDetailOptions.optionsFor(null)).isEmpty();
	}
}
