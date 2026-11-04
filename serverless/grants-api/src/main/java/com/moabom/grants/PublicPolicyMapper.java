package com.moabom.grants;

import java.util.List;
import java.util.Locale;

/**
 * Maps a public-policy shaped row onto the grant model.
 * Missing age, region, income, household, or special-condition values stay unknown.
 * A date that is not yyyy-MM-dd is rejected instead of being rewritten.
 */
public final class PublicPolicyMapper {
    private PublicPolicyMapper() {
    }

    public static Grant toGrant(PublicPolicyRecord source) {
        if (source == null) {
            throw new IllegalArgumentException("공개 정책 원천이 없습니다.");
        }
        String serviceId = source.serviceId() == null ? "" : source.serviceId().trim();
        String serviceName = source.serviceName() == null ? "" : source.serviceName().trim();
        if (serviceId.isEmpty() || serviceName.isEmpty()) {
            throw new IllegalArgumentException("서비스 ID와 서비스명은 비울 수 없습니다.");
        }
        String sourceName = source.sourceName() == null || source.sourceName().isBlank()
                ? "public-policy"
                : source.sourceName().trim();
        RequirementClarity householdClarity = specifiedOrUnknown(source.householdClarity(), source.householdTypes());
        RequirementClarity specialClarity = specifiedOrUnknown(source.specialClarity(), source.specialConditions());
        Eligibility eligibility = new Eligibility(
                null,
                source.minAge(),
                source.maxAge(),
                source.minIncomeBracket(),
                source.maxIncomeBracket(),
                blankToNull(source.regionText()),
                householdClarity,
                householdClarity == RequirementClarity.SPECIFIED ? source.householdTypes() : List.of(),
                specialClarity,
                specialClarity == RequirementClarity.SPECIFIED ? source.specialConditions() : List.of()
        );
        String sourceUrl = blankToNull(source.serviceUrl());
        String applicationLink = blankToNull(source.applicationLink());
        return new Grant(
                grantId(serviceId),
                "미분류",
                serviceName,
                blankToNull(source.summary()),
                isoOrReject(source.applicationEnd(), "신청 마감일"),
                applicationLink != null ? applicationLink : sourceUrl,
                eligibility,
                source.sample(),
                List.of(),
                sourceUrl,
                isoOrReject(source.referenceDate(), "기준일"),
                blankToNull(source.referenceText()),
                isoOrReject(source.applicationStart(), "신청 시작일"),
                sourceName + ":" + serviceId
        );
    }

    private static String isoOrReject(String value, String label) {
        String text = blankToNull(value);
        if (text == null) {
            return null;
        }
        try {
            java.time.LocalDate.parse(text);
            return text;
        } catch (java.time.format.DateTimeParseException ex) {
            throw new IllegalArgumentException(label + "은 yyyy-MM-dd 형식만 저장합니다. 입력값을 다른 날짜로 바꾸지 않습니다: " + text);
        }
    }

    static String grantId(String serviceId) {
        String folded = serviceId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-");
        folded = folded.replaceAll("-+", "-");
        if (folded.startsWith("-")) {
            folded = folded.substring(1);
        }
        if (folded.endsWith("-")) {
            folded = folded.substring(0, folded.length() - 1);
        }
        String grantId = "policy-" + folded;
        if (grantId.length() > 80) {
            grantId = grantId.substring(0, 80);
        }
        return grantId;
    }

    private static RequirementClarity specifiedOrUnknown(RequirementClarity clarity, List<String> values) {
        if (clarity == null) {
            return RequirementClarity.UNKNOWN;
        }
        if (clarity == RequirementClarity.SPECIFIED && (values == null || values.isEmpty())) {
            return RequirementClarity.UNKNOWN;
        }
        return clarity;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
