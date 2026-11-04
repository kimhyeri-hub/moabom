package com.moabom.grants;

import com.moabom.grants.local.LocalCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficialGrantCatalogTest {
    @Test
    void officialFileKeepsConfirmedFieldsAndLeavesUnknownBoundsEmpty() throws Exception {
        List<Grant> samples = LocalCatalog.load();
        List<Grant> official = LocalCatalog.loadOfficial();

        assertEquals(8, samples.size());
        assertTrue(samples.stream().allMatch(Grant::sample));
        assertEquals(12, official.size());
        assertTrue(official.stream().allMatch(grant ->
                !grant.sample()
                        && grant.sourceUrl() != null
                        && grant.sourceUrl().startsWith("https://")
                        && grant.link().equals(grant.sourceUrl())
                        && grant.referenceDate().equals("2026-11-04")
                        && grant.applicationStart() == null
                        && grant.endDate() == null
                        && grant.eligibility().minIncomeBracket() == null
                        && grant.eligibility().maxIncomeBracket() == null
                        && !grant.categoryTitle().equals("미분류")
                        && !grant.keywords().isEmpty()));

        Grant anyang = grant(official, "policy-383000000209");
        assertEquals("안양시", anyang.eligibility().targetRegion());
        assertEquals(19, anyang.eligibility().minAge());
        assertEquals(39, anyang.eligibility().maxAge());

        Grant geoje = grant(official, "policy-537000001436");
        assertEquals("거제시", geoje.eligibility().targetRegion());

        Grant children = grant(official, "policy-999000000090");
        assertEquals(RequirementClarity.SPECIFIED, children.eligibility().householdClarity());
        assertEquals(List.of("MULTI_CHILD"), children.eligibility().householdTypes());
        assertNull(children.eligibility().minAge());

        Grant employment = grant(official, "policy-149200005007");
        assertNull(employment.eligibility().minAge());
        assertNull(employment.eligibility().targetRegion());
    }

    @Test
    void recommendationUsesOfficialPoliciesWithoutChangingTheScoreWeights() throws Exception {
        assertEquals(0.6, RecommendationScorer.CONTENT_WEIGHT);
        assertEquals(0.4, RecommendationScorer.BEHAVIOR_WEIGHT);
        assertEquals(1.0, RecommendationScorer.VIEW_WEIGHT);
        assertEquals(2.0, RecommendationScorer.SCRAP_WEIGHT);
        assertEquals(5, RecommendationScorer.TOP_N);

        List<Grant> official = LocalCatalog.loadOfficial();
        UserProfile youth = new UserProfile("youth", 25, "서울특별시", 3, List.of("SINGLE"), List.of());
        List<String> ids = ids(RecommendationScorer.recommend(youth, official, List.of()));

        assertEquals(5, ids.size());
        assertFalse(ids.contains("policy-383000000209"));
        assertFalse(ids.contains("policy-537000001436"));
        assertFalse(ids.contains("policy-650000001119"));
        assertFalse(ids.contains("policy-999000000090"));
        assertEquals(
                EligibilityStatus.CHECK_REQUIRED,
                RecommendationScorer.recommend(youth, official, List.of()).get(0).eligibilityStatus());

        assertEquals(
                EligibilityStatus.INELIGIBLE,
                EligibilityEvaluator.assess(youth, grant(official, "policy-999000000090").eligibility()).status());
        UserProfile parent = new UserProfile("parent", 25, "서울특별시", 3, List.of("MULTI_CHILD"), List.of());
        assertEquals(
                EligibilityStatus.CHECK_REQUIRED,
                EligibilityEvaluator.assess(parent, grant(official, "policy-999000000090").eligibility()).status());

        List<UserBehavior> scrap = List.of(
                new UserBehavior("youth", BehaviorType.SCRAP, "policy-161300000099", "2026-11-04T00:00:00Z"));
        assertEquals(
                "policy-161300000099",
                RecommendationScorer.recommend(youth, official, scrap).get(0).grant().grantId());
    }

    private static Grant grant(List<Grant> grants, String id) {
        return grants.stream().filter(grant -> grant.grantId().equals(id)).findFirst().orElseThrow();
    }

    private static List<String> ids(List<RecommendationItem> items) {
        return items.stream().map(item -> item.grant().grantId()).toList();
    }
}
