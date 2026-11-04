package com.moabom.grants;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationScorerTest {
    @Test
    void excludesGrantWhenAgeDoesNotMatch() {
        UserProfile user = TestGrants.user("example-user-age", 18, "예시특별시", 3);
        Grant tooOld = TestGrants.grant("example-too-old", "교통", List.of("버스"), 19, 34, "예시특별시", 1, 5);
        Grant allowed = TestGrants.grant("example-allowed-age", "교통", List.of("버스"), 18, 34, "예시특별시", 1, 5);
        List<UserBehavior> history = List.of(
                TestGrants.behavior(user.userId(), BehaviorType.SCRAP, tooOld.grantId(), "2026-11-01T00:00:00Z")
        );

        List<RecommendationItem> results = RecommendationScorer.recommend(user, List.of(tooOld, allowed), history);

        assertFalse(ids(results).contains(tooOld.grantId()));
        assertTrue(ids(results).contains(allowed.grantId()));
        assertEquals(EligibilityStatus.ELIGIBLE, results.get(0).eligibilityStatus());
    }

    @Test
    void excludesGrantWhenRegionDoesNotMatch() {
        UserProfile user = TestGrants.user("example-user-region", 30, "예시도", 3);
        Grant otherRegion = TestGrants.grant("example-other-region", "주거", List.of("월세"), 19, 39, "예시특별시", 1, 6);
        Grant sameRegion = TestGrants.grant("example-same-region", "주거", List.of("월세"), 19, 39, "예시도", 1, 6);

        List<RecommendationItem> results = RecommendationScorer.recommend(
                user, List.of(otherRegion, sameRegion), List.of());

        assertFalse(ids(results).contains(otherRegion.grantId()));
        assertEquals(List.of(sameRegion.grantId()), ids(results));
    }

    @Test
    void excludesGrantWhenIncomeDoesNotMatch() {
        UserProfile user = TestGrants.user("example-user-income", 30, "예시특별시", 6);
        Grant tooLow = TestGrants.grant("example-income-low", "생활", List.of("장년"), 19, 39, "예시특별시", 1, 5);
        Grant inclusive = TestGrants.grant("example-income-ok", "생활", List.of("장년"), 19, 39, "예시특별시", 1, 6);

        List<RecommendationItem> results = RecommendationScorer.recommend(
                user, List.of(tooLow, inclusive), List.of());

        assertFalse(ids(results).contains(tooLow.grantId()));
        assertEquals(List.of(inclusive.grantId()), ids(results));
    }

    @Test
    void marksMissingPolicyInformationAsCheckRequired() {
        UserProfile user = TestGrants.user("example-user-check", 30, "예시특별시", 3);
        Grant missingIncome = TestGrants.grant(
                "example-missing-income", "생활", List.of("확인"), 19, 39, "전국(예시)", null, null);

        List<RecommendationItem> results = RecommendationScorer.recommend(user, List.of(missingIncome), List.of());

        assertEquals(1, results.size());
        assertEquals(EligibilityStatus.CHECK_REQUIRED, results.get(0).eligibilityStatus());
        assertTrue(results.get(0).eligibilityChecks().stream()
                .anyMatch(check -> check.dimension().equals("INCOME") && check.result() == CheckResult.UNKNOWN));
    }

    @Test
    void ranksScrappedGrantAboveViewedGrant() {
        UserProfile user = TestGrants.user("example-user-rank", 30, "예시특별시", 3);
        Grant scrapped = TestGrants.grant("example-scrapped", "교통", List.of("버스"), 19, 39, "예시특별시", 1, 6);
        Grant viewed = TestGrants.grant("example-viewed", "교통", List.of("버스"), 19, 39, "예시특별시", 1, 6);
        List<UserBehavior> history = List.of(
                TestGrants.behavior(user.userId(), BehaviorType.VIEW, viewed.grantId(), "2026-11-01T00:00:00Z"),
                TestGrants.behavior(user.userId(), BehaviorType.SCRAP, scrapped.grantId(), "2026-11-02T00:00:00Z")
        );

        List<RecommendationItem> results = RecommendationScorer.recommend(user, List.of(viewed, scrapped), history);

        RecommendationItem scrapItem = find(results, scrapped.grantId());
        RecommendationItem viewItem = find(results, viewed.grantId());
        assertEquals(scrapItem.contentSimilarity(), viewItem.contentSimilarity());
        assertTrue(scrapItem.behaviorInterest() > viewItem.behaviorInterest());
        assertTrue(scrapItem.score() > viewItem.score());
        assertEquals(1, scrapItem.rank());
        assertTrue(scrapItem.reasons().stream().anyMatch(reason -> reason.contains("스크랩")));
        assertTrue(viewItem.reasons().stream().anyMatch(reason -> reason.contains("조회했습니다")));
    }

    @Test
    void recommendsEligibleGrantsForUserWithoutBehavior() {
        UserProfile user = TestGrants.user("example-user-new", 30, "예시특별시", 3);
        Grant later = TestGrants.grant("example-b-new", "생활", List.of("장년"), 19, 39, "예시특별시", 1, 6);
        Grant earlier = TestGrants.grant("example-a-new", "교통", List.of("버스"), 19, 39, "예시특별시", 1, 6);
        Grant blocked = TestGrants.grant("example-c-blocked", "주거", List.of("월세"), 19, 39, "다른예시시", 1, 6);

        List<RecommendationItem> results = RecommendationScorer.recommend(
                user, List.of(later, blocked, earlier), List.of());

        assertEquals(List.of(earlier.grantId(), later.grantId()), ids(results));
        assertEquals(0.0, results.get(0).score());
        assertEquals(0.0, results.get(0).contentSimilarity());
        assertEquals(0.0, results.get(0).behaviorInterest());
        assertTrue(results.get(0).reasons().stream().anyMatch(reason -> reason.contains("기본 추천")));
    }

    @Test
    void returnsAtMostFiveRecommendations() {
        UserProfile user = TestGrants.user("example-user-top", 30, "예시특별시", 3);
        List<Grant> grants = new ArrayList<>();
        for (String suffix : List.of("a", "b", "c", "d", "e", "f")) {
            grants.add(TestGrants.grant(
                    "example-top-" + suffix, "생활", List.of("장년"), 19, 39, "예시특별시", 1, 6));
        }

        List<RecommendationItem> results = RecommendationScorer.recommend(user, grants, List.of());

        assertEquals(5, results.size());
        assertEquals(List.of(
                "example-top-a", "example-top-b", "example-top-c", "example-top-d", "example-top-e"
        ), ids(results));
    }

    @Test
    void returnsTheSameRankingForTheSameInput() {
        UserProfile user = TestGrants.user("example-user-stable", 30, "예시특별시", 3);
        Grant first = TestGrants.grant("example-stable-a", "교통", List.of("버스"), 19, 39, "예시특별시", 1, 6);
        Grant second = TestGrants.grant("example-stable-b", "돌봄", List.of("양육"), 19, 39, "예시특별시", 1, 6);
        List<Grant> grants = List.of(second, first);
        List<UserBehavior> history = List.of(
                TestGrants.behavior(user.userId(), BehaviorType.VIEW, first.grantId(), "2026-11-01T00:00:00Z"),
                TestGrants.behavior(user.userId(), BehaviorType.SCRAP, second.grantId(), "2026-11-02T00:00:00Z")
        );
        List<Grant> reversedGrants = new ArrayList<>(grants);
        Collections.reverse(reversedGrants);
        List<UserBehavior> reversedHistory = new ArrayList<>(history);
        Collections.reverse(reversedHistory);

        List<RecommendationItem> once = RecommendationScorer.recommend(user, grants, history);
        List<RecommendationItem> twice = RecommendationScorer.recommend(user, reversedGrants, reversedHistory);

        assertEquals(ids(once), ids(twice));
        assertEquals(once.get(0).score(), twice.get(0).score());
        assertEquals(once.get(0).reasons(), twice.get(0).reasons());
        assertEquals(once.get(1).contentSimilarity(), twice.get(1).contentSimilarity());
        assertFalse(ScoringPolicy.initial().optimized());
    }

    private static RecommendationItem find(List<RecommendationItem> results, String grantId) {
        return results.stream()
                .filter(item -> item.grant().grantId().equals(grantId))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> ids(List<RecommendationItem> results) {
        return results.stream().map(item -> item.grant().grantId()).toList();
    }
}
