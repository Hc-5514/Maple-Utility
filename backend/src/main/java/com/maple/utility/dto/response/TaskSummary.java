package com.maple.utility.dto.response;

import java.io.Serializable;

public record TaskSummary(
		int completed,
		int total
) implements Serializable {
}
