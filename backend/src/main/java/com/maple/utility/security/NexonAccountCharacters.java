package com.maple.utility.security;

import java.util.List;
import java.util.Set;

public record NexonAccountCharacters(Set<String> accountIds, List<NexonCharacterSummary> characters) { }
