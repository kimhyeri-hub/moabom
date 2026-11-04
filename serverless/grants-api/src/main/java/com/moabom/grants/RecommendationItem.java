package com.moabom.grants;

import java.util.List;

public record RecommendationItem(
        int rank,
        double score,
        double contentSimilarity,
        double behaviorInterest,
        EligibilityStatus eligibilityStatus,
        List<DimensionCheck> eligibilityChecks,
        List<String> reasons,
        Grant grant
) {
}
