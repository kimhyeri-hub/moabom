package com.moabom.grants;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EligibilityConditionsTest {
    @Test
    void householdMatchPassesAndMismatchFails() {
        UserProfile user = new UserProfile("example-user", 30, "예시특별시", 3, List.of("SINGLE"), List.of());
        Eligibility match = eligibility(RequirementClarity.SPECIFIED, List.of("SINGLE"), RequirementClarity.UNRESTRICTED, List.of());
        Eligibility miss = eligibility(RequirementClarity.SPECIFIED, List.of("SINGLE_PARENT"), RequirementClarity.UNRESTRICTED, List.of());

        assertEquals(EligibilityStatus.ELIGIBLE, EligibilityEvaluator.assess(user, match).status());
        EligibilityAssessment failed = EligibilityEvaluator.assess(user, miss);
        assertEquals(EligibilityStatus.INELIGIBLE, failed.status());
        assertTrue(failed.checks().stream().anyMatch(check ->
                check.dimension().equals("HOUSEHOLD") && check.result() == CheckResult.FAIL));
    }

    @Test
    void missingUserOrPolicyConditionStaysUnknown() {
        UserProfile silent = new UserProfile("example-user", 30, "예시특별시", 3);
        Eligibility specified = eligibility(RequirementClarity.SPECIFIED, List.of("SINGLE"), RequirementClarity.SPECIFIED, List.of("DISABLED"));
        EligibilityAssessment missingUser = EligibilityEvaluator.assess(silent, specified);
        assertEquals(EligibilityStatus.CHECK_REQUIRED, missingUser.status());
        assertTrue(missingUser.checks().stream().anyMatch(check ->
                check.dimension().equals("SPECIAL") && check.result() == CheckResult.UNKNOWN));

        UserProfile answered = new UserProfile("example-user", 30, "예시특별시", 3, List.of("SINGLE"), List.of("DISABLED"));
        Eligibility unclear = eligibility(RequirementClarity.UNKNOWN, List.of(), RequirementClarity.UNKNOWN, List.of());
        EligibilityAssessment missingPolicy = EligibilityEvaluator.assess(answered, unclear);
        assertEquals(EligibilityStatus.CHECK_REQUIRED, missingPolicy.status());
        assertTrue(missingPolicy.checks().stream().noneMatch(check -> check.result() == CheckResult.FAIL));
    }

    @Test
    void existingAgeRegionIncomeRulesStayInPlace() {
        UserProfile user = new UserProfile("example-user", 70, "다른지역", 9);
        Grant grant = TestGrants.grant("example-age", "생활", List.of("장년"), 19, 39, "예시특별시", 1, 6);
        assertEquals(EligibilityStatus.INELIGIBLE, EligibilityEvaluator.assess(user, grant.eligibility()).status());
        assertEquals(0.6, RecommendationScorer.CONTENT_WEIGHT);
        assertEquals(0.4, RecommendationScorer.BEHAVIOR_WEIGHT);
    }

    @Test
    void profileRejectsUnknownCodesAndKeepsOptionalLists() {
        UserProfile parsed = ProfileValidator.parse("local-user",
                "{\"age\":25,\"region\":\"예시특별시\",\"incomeBracket\":3,"
                        + "\"householdTypes\":[\"SINGLE\"],\"specialConditions\":[\"DISABLED\"]}");
        assertEquals(List.of("SINGLE"), parsed.householdTypes());
        assertEquals(List.of("DISABLED"), parsed.specialConditions());
        assertThrows(IllegalArgumentException.class, () -> ProfileValidator.parse("local-user",
                "{\"age\":25,\"region\":\"예시특별시\",\"incomeBracket\":3,\"householdTypes\":[\"UNKNOWN_CODE\"]}"));
    }

    @Test
    void publicRowDoesNotInventMissingBounds() {
        PublicPolicyRecord source = new PublicPolicyRecord(
                "복지로-중앙부처",
                "WLF-100",
                "공개 정책 예시",
                "원문에 연령 숫자가 없습니다.",
                "https://www.bokjiro.go.kr/ssis-tbu/twataa/wlfareInfo/moveTWAT52011M.do",
                null,
                "2026",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                RequirementClarity.UNKNOWN,
                List.of(),
                true
        );
        Grant grant = PublicPolicyMapper.toGrant(source);
        assertTrue(grant.sample());
        assertNull(grant.eligibility().minAge());
        assertNull(grant.eligibility().targetRegion());
        assertEquals(RequirementClarity.UNKNOWN, grant.eligibility().householdClarity());
        assertEquals("2026", grant.referenceText());
        assertNull(grant.referenceDate());
        UserProfile user = new UserProfile("example-user", 25, "예시특별시", 3, List.of("SINGLE"), List.of("DISABLED"));
        assertEquals(EligibilityStatus.CHECK_REQUIRED, EligibilityEvaluator.assess(user, grant.eligibility()).status());
    }

    @Test
    void normalizedTitleAndSourceIdentityDetectDuplicates() {
        Grant first = policy("policy-a", "bokji:100", "청년  교통 지원", "예시특별시");
        Grant spaced = policy("policy-b", "bokji:200", "청년 교통 지원", "예시특별시");
        Grant sameSource = policy("policy-c", "bokji:100", "다른 이름", "예시도");
        Grant otherRegion = policy("policy-d", "bokji:300", "청년 교통 지원", "예시도");

        assertEquals(List.of(List.of("policy-a", "policy-b", "policy-c")), PolicyDuplicateFinder.duplicateGroups(
                List.of(first, spaced, sameSource, otherRegion)
        ));
    }

    private static Eligibility eligibility(
            RequirementClarity householdClarity,
            List<String> households,
            RequirementClarity specialClarity,
            List<String> specials
    ) {
        return new Eligibility(
                "예시",
                19,
                39,
                1,
                6,
                "예시특별시",
                householdClarity,
                households,
                specialClarity,
                specials
        );
    }

    private static Grant policy(String id, String sourceId, String title, String region) {
        return new Grant(
                id,
                "교통",
                title,
                "실제 수집 데이터가 아닙니다.",
                "2026-12-31",
                "https://example.com/" + id,
                new Eligibility("예시", 19, 39, 1, 6, region),
                true,
                List.of("교통"),
                "https://example.com/" + id,
                "2026-08-19",
                "2026-08-19",
                "2026-01-01",
                sourceId
        );
    }
}
