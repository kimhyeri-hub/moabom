package com.moabom.grants;

public record ScoringPolicy(
        String formula,
        String contentSimilarityRange,
        String behaviorInterestRange,
        String scoreRange,
        boolean optimized,
        String note
) {
    public static ScoringPolicy initial() {
        return new ScoringPolicy(
                "score = 0.6 * contentSimilarity + 0.4 * behaviorInterest",
                "[0, 1]",
                "[0, 1]",
                "[0, 1]",
                false,
                "초기 가중치이며 데이터로 최적화한 값이 아닙니다."
        );
    }
}
