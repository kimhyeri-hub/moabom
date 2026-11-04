package com.moabom.grants;

import java.util.List;

public record RecommendationResponse(
        String userId,
        boolean localDevOnly,
        ScoringPolicy scoring,
        List<RecommendationItem> recommendations
) {
}
