package com.moabom.grants;

import com.moabom.grants.local.LocalCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentPersonalizationTest {
    @Test
    void keywordsDoNotChangeScoreUntilTheUserStatesAnInterest() throws Exception {
        List<Grant> official = LocalCatalog.loadOfficial();
        UserProfile blank = new UserProfile("new-user", 25, "서울특별시", 3);
        List<RecommendationItem> results = RecommendationScorer.recommend(blank, official, List.of());

        assertEquals(0.6, RecommendationScorer.CONTENT_WEIGHT);
        assertEquals(0.4, RecommendationScorer.BEHAVIOR_WEIGHT);
        assertTrue(official.stream().noneMatch(grant -> grant.keywords().isEmpty()));
        assertTrue(results.stream().allMatch(item -> item.contentSimilarity() == 0.0));
        assertEquals(0.0, results.get(0).score());
        assertEquals(EligibilityStatus.CHECK_REQUIRED, results.get(0).eligibilityStatus());
    }

    @Test
    void differentInterestsRankDifferentPoliciesForNewUsers() throws Exception {
        List<Grant> official = LocalCatalog.loadOfficial();
        UserProfile housing = user("user-a", 25, "서울특별시", List.of("주거"), List.of());
        UserProfile education = user("user-b", 25, "서울특별시", List.of("교육"), List.of("SINGLE"));

        List<RecommendationItem> housingResults = RecommendationScorer.recommend(housing, official, List.of());
        List<RecommendationItem> educationResults = RecommendationScorer.recommend(education, official, List.of());

        assertEquals("주거", housingResults.get(0).grant().categoryTitle());
        assertEquals("교육", educationResults.get(0).grant().categoryTitle());
        assertTrue(housingResults.get(0).contentSimilarity() > 0.0);
        assertTrue(educationResults.get(0).contentSimilarity() > 0.0);
        assertNotEquals(housingResults.get(0).grant().grantId(), educationResults.get(0).grant().grantId());
        assertTrue(housingResults.get(0).reasons().stream().anyMatch(reason -> reason.contains("관심 분야")));
        assertEquals(EligibilityStatus.CHECK_REQUIRED, housingResults.get(0).eligibilityStatus());
        assertFalse(ids(housingResults).contains("policy-383000000209"));
        assertFalse(ids(housingResults).contains("policy-537000001436"));
        assertFalse(ids(housingResults).contains("policy-650000001119"));
        assertFalse(ids(educationResults).contains("policy-999000000090"));
    }

    @Test
    void scrapMovesAMatchingGrantAheadWithoutChangingWeights() throws Exception {
        List<Grant> official = LocalCatalog.loadOfficial();
        UserProfile housing = user("user-a", 25, "서울특별시", List.of("주거"), List.of());
        List<String> before = ids(RecommendationScorer.recommend(housing, official, List.of()));
        List<UserBehavior> scrap = List.of(
                new UserBehavior("user-a", BehaviorType.SCRAP, "policy-161300000099", "2026-11-04T00:00:00Z"));
        List<RecommendationItem> after = RecommendationScorer.recommend(housing, official, scrap);

        assertEquals("policy-161300000099", after.get(0).grant().grantId());
        assertNotEquals(before.get(0), after.get(0).grant().grantId());
        assertTrue(after.get(0).reasons().stream().anyMatch(reason -> reason.contains("스크랩")));
        assertTrue(after.get(0).score() > after.get(1).score());
    }

    @Test
    void rejectsAnUnknownInterestLabel() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> ProfileValidator.parse(
                "user-a",
                "{\"age\":25,\"region\":\"서울특별시\",\"incomeBracket\":3,\"interests\":[\"중위소득\"]}"));
    }

    private static UserProfile user(
            String id,
            int age,
            String region,
            List<String> interests,
            List<String> households
    ) {
        return new UserProfile(id, age, region, 3, households, List.of(), interests);
    }

    private static List<String> ids(List<RecommendationItem> items) {
        return items.stream().map(item -> item.grant().grantId()).toList();
    }
}
