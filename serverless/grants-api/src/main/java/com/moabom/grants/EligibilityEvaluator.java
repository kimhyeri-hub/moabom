package com.moabom.grants;

import java.util.List;

/**
 * Compares a user profile with grant eligibility.
 * A known mismatch is FAIL. A missing user value or policy bound is UNKNOWN, not a pass.
 * FAIL outranks UNKNOWN. Any FAIL makes the grant INELIGIBLE.
 */
public final class EligibilityEvaluator {
    private EligibilityEvaluator() {
    }

    public static EligibilityAssessment assess(UserProfile user, Eligibility eligibility) {
        List<DimensionCheck> checks = List.of(
                age(user, eligibility),
                region(user, eligibility),
                income(user, eligibility),
                household(user, eligibility),
                special(user, eligibility)
        );
        boolean failed = checks.stream().anyMatch(check -> check.result() == CheckResult.FAIL);
        boolean unknown = checks.stream().anyMatch(check -> check.result() == CheckResult.UNKNOWN);
        EligibilityStatus status = failed
                ? EligibilityStatus.INELIGIBLE
                : unknown ? EligibilityStatus.CHECK_REQUIRED : EligibilityStatus.ELIGIBLE;
        return new EligibilityAssessment(status, checks);
    }

    private static DimensionCheck age(UserProfile user, Eligibility eligibility) {
        Integer min = eligibility == null ? null : eligibility.minAge();
        Integer max = eligibility == null ? null : eligibility.maxAge();
        Integer age = user.age();
        if (min == null && max == null) {
            return unknown("AGE", "지원금에 연령 조건이 없습니다.");
        }
        if (age == null) {
            return unknown("AGE", "사용자 연령이 없어 연령 조건을 확인하지 못했습니다.");
        }
        if (min != null && age < min) {
            return fail("AGE", "연령 " + age + "세는 최소 " + min + "세보다 어립니다.");
        }
        if (max != null && age > max) {
            return fail("AGE", "연령 " + age + "세는 최대 " + max + "세를 초과합니다.");
        }
        if (min == null || max == null) {
            return unknown("AGE", "지원금의 연령 상한 또는 하한 중 하나만 있어 확인이 필요합니다.");
        }
        return pass("AGE", "연령 " + age + "세가 " + min + "세 이상 " + max + "세 이하입니다.");
    }

    private static DimensionCheck region(UserProfile user, Eligibility eligibility) {
        String target = blankToNull(eligibility == null ? null : eligibility.targetRegion());
        String region = blankToNull(user.region());
        if (target == null) {
            return unknown("REGION", "지원금에 지역 조건이 없습니다.");
        }
        if (target.startsWith("전국")) {
            return pass("REGION", "지역 제한이 없는 지원금입니다.");
        }
        if (region == null) {
            return unknown("REGION", "사용자 거주지역이 없어 지역 조건을 확인하지 못했습니다.");
        }
        if (region.equals(target)) {
            return pass("REGION", "거주지역이 대상 지역 " + target + "과 같습니다.");
        }
        return fail("REGION", "거주지역 " + region + "은 대상 지역 " + target + "과 다릅니다.");
    }

    private static DimensionCheck income(UserProfile user, Eligibility eligibility) {
        Integer min = eligibility == null ? null : eligibility.minIncomeBracket();
        Integer max = eligibility == null ? null : eligibility.maxIncomeBracket();
        Integer bracket = user.incomeBracket();
        if (min == null && max == null) {
            return unknown("INCOME", "지원금에 소득분위 조건이 없습니다.");
        }
        if (bracket == null) {
            return unknown("INCOME", "사용자 소득분위가 없어 소득 조건을 확인하지 못했습니다.");
        }
        if (min != null && bracket < min) {
            return fail("INCOME", "소득분위 " + bracket + "은 최소 " + min + "분위보다 낮습니다.");
        }
        if (max != null && bracket > max) {
            return fail("INCOME", "소득분위 " + bracket + "은 최대 " + max + "분위를 초과합니다.");
        }
        if (min == null || max == null) {
            return unknown("INCOME", "지원금의 소득분위 상한 또는 하한 중 하나만 있어 확인이 필요합니다.");
        }
        return pass("INCOME", "소득분위 " + bracket + "이 " + min + "분위 이상 " + max + "분위 이하입니다.");
    }


    private static DimensionCheck household(UserProfile user, Eligibility eligibility) {
        return coded(
                "HOUSEHOLD",
                eligibility == null ? RequirementClarity.UNKNOWN : eligibility.householdClarity(),
                eligibility == null ? java.util.List.of() : eligibility.householdTypes(),
                user.householdTypes(),
                "가구 형태 제한이 없습니다.",
                "정책의 가구 형태 조건이 확인되지 않아 추정하지 않습니다.",
                "사용자의 가구 형태 정보가 없습니다.",
                "가구 형태가 정책 대상과 겹칩니다.",
                "가구 형태가 정책 대상과 겹치지 않습니다."
        );
    }

    private static DimensionCheck special(UserProfile user, Eligibility eligibility) {
        return coded(
                "SPECIAL",
                eligibility == null ? RequirementClarity.UNKNOWN : eligibility.specialClarity(),
                eligibility == null ? java.util.List.of() : eligibility.specialConditions(),
                user.specialConditions(),
                "특수조건 제한이 없습니다.",
                "정책의 특수조건이 확인되지 않아 추정하지 않습니다.",
                "사용자의 특수조건 정보가 없습니다.",
                "특수조건이 정책 대상과 겹칩니다.",
                "특수조건이 정책 대상과 겹치지 않습니다."
        );
    }

    private static DimensionCheck coded(
            String dimension,
            RequirementClarity clarity,
            java.util.List<String> required,
            java.util.List<String> provided,
            String unrestricted,
            String policyUnknown,
            String userUnknown,
            String matched,
            String unmatched
    ) {
        if (clarity == RequirementClarity.UNRESTRICTED) {
            return pass(dimension, unrestricted);
        }
        if (clarity == null || clarity == RequirementClarity.UNKNOWN) {
            return unknown(dimension, policyUnknown);
        }
        if (provided == null || provided.isEmpty()) {
            return unknown(dimension, userUnknown);
        }
        boolean overlaps = provided.stream().anyMatch(required::contains);
        return overlaps ? pass(dimension, matched) : fail(dimension, unmatched);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static DimensionCheck pass(String dimension, String detail) {
        return new DimensionCheck(dimension, CheckResult.PASS, detail);
    }

    private static DimensionCheck fail(String dimension, String detail) {
        return new DimensionCheck(dimension, CheckResult.FAIL, detail);
    }

    private static DimensionCheck unknown(String dimension, String detail) {
        return new DimensionCheck(dimension, CheckResult.UNKNOWN, detail);
    }
}
