package com.maple.utility.dto.response;

import com.maple.utility.entity.HuntingGround;

public record HuntingGroundResponse(Long id, String regionName, String mapName, int maxMonsterLevel, boolean favorite) {
	public static HuntingGroundResponse from(HuntingGround ground, boolean favorite) {
		return new HuntingGroundResponse(ground.getId(), ground.getRegionName(), ground.getMapName(), ground.getMaxMonsterLevel(), favorite);
	}
}
