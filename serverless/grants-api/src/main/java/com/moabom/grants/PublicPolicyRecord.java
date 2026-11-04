package com.moabom.grants;

import java.util.List;

/**
 * One row shaped like a public welfare record.
 * Blank eligibility fields stay blank. This type does not parse free-text criteria into numbers.
 * applicationLink is the application page when it differs from the source URL.
 */
public record PublicPolicyRecord(
        String sourceName,
        String serviceId,
        String serviceName,
        String summary,
        String serviceUrl,
        String referenceDate,
        String referenceText,
        String applicationStart,
        String applicationEnd,
        String regionText,
        Integer minAge,
        Integer maxAge,
        Integer minIncomeBracket,
        Integer maxIncomeBracket,
        RequirementClarity householdClarity,
        List<String> householdTypes,
        RequirementClarity specialClarity,
        List<String> specialConditions,
        boolean sample,
        String applicationLink
) {
    public PublicPolicyRecord(
            String sourceName,
            String serviceId,
            String serviceName,
            String summary,
            String serviceUrl,
            String referenceDate,
            String referenceText,
            String applicationStart,
            String applicationEnd,
            String regionText,
            Integer minAge,
            Integer maxAge,
            Integer minIncomeBracket,
            Integer maxIncomeBracket,
            RequirementClarity householdClarity,
            List<String> householdTypes,
            RequirementClarity specialClarity,
            List<String> specialConditions,
            boolean sample
    ) {
        this(
                sourceName,
                serviceId,
                serviceName,
                summary,
                serviceUrl,
                referenceDate,
                referenceText,
                applicationStart,
                applicationEnd,
                regionText,
                minAge,
                maxAge,
                minIncomeBracket,
                maxIncomeBracket,
                householdClarity,
                householdTypes,
                specialClarity,
                specialConditions,
                sample,
                null
        );
    }
}
