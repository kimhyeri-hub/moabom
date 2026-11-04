package com.moabom.grants;

import java.util.List;

/**
 * Eligibility fields kept flat in DynamoDB so a recommendation query can read them.
 * Names follow the Django GrantEligibility model, with household and special conditions added.
 * A null age, region, or income bound stays unknown. It is not treated as unrestricted.
 * UNRESTRICTED is stored only when the source explicitly says there is no limit.
 * A missing household or special-condition value stays UNKNOWN, including on sample grants.
 */
public record Eligibility(
        String targetAgeGroup,
        Integer minAge,
        Integer maxAge,
        Integer minIncomeBracket,
        Integer maxIncomeBracket,
        String targetRegion,
        RequirementClarity householdClarity,
        List<String> householdTypes,
        RequirementClarity specialClarity,
        List<String> specialConditions
) {
    public Eligibility {
        householdClarity = householdClarity == null ? RequirementClarity.UNKNOWN : householdClarity;
        specialClarity = specialClarity == null ? RequirementClarity.UNKNOWN : specialClarity;
        householdTypes = PolicyCodes.normalize(householdTypes, PolicyCodes.HOUSEHOLDS, "가구 형태");
        specialConditions = PolicyCodes.normalize(specialConditions, PolicyCodes.SPECIALS, "특수조건");
        if (householdClarity != RequirementClarity.SPECIFIED) {
            householdTypes = List.of();
        }
        if (specialClarity != RequirementClarity.SPECIFIED) {
            specialConditions = List.of();
        }
    }

    public Eligibility(
            String targetAgeGroup,
            Integer minAge,
            Integer maxAge,
            Integer minIncomeBracket,
            Integer maxIncomeBracket,
            String targetRegion
    ) {
        this(
                targetAgeGroup,
                minAge,
                maxAge,
                minIncomeBracket,
                maxIncomeBracket,
                targetRegion,
                RequirementClarity.UNRESTRICTED,
                List.of(),
                RequirementClarity.UNRESTRICTED,
                List.of()
        );
    }
}
