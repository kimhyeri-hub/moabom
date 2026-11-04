package com.moabom.grants;

import java.util.List;

final class TestGrants {
    private TestGrants() {
    }

    static UserProfile user(String id, Integer age, String region, Integer income) {
        return new UserProfile(id, age, region, income);
    }

    static Grant grant(
            String id,
            String category,
            List<String> keywords,
            Integer minAge,
            Integer maxAge,
            String region,
            Integer minIncome,
            Integer maxIncome
    ) {
        return new Grant(
                id,
                category,
                "[예시] " + id,
                "실제 정책이 아닙니다.",
                null,
                "https://example.com/moabom/grants/" + id,
                new Eligibility("예시", minAge, maxAge, minIncome, maxIncome, region),
                false,
                keywords
        );
    }

    static UserBehavior behavior(String userId, BehaviorType type, String grantId, String occurredAt) {
        return new UserBehavior(userId, type, grantId, occurredAt);
    }
}
