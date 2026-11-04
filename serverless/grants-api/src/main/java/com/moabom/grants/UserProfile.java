package com.moabom.grants;

import java.util.List;

/** Local-development profile. Missing dimensions stay missing and are not inferred. */
public record UserProfile(
        String userId,
        Integer age,
        String region,
        Integer incomeBracket,
        List<String> householdTypes,
        List<String> specialConditions,
        List<String> interests
) {
    public UserProfile {
        householdTypes = PolicyCodes.normalize(householdTypes, PolicyCodes.HOUSEHOLDS, "가구 형태");
        specialConditions = PolicyCodes.normalize(specialConditions, PolicyCodes.SPECIALS, "특수조건");
        interests = InterestLabels.normalize(interests);
    }

    public UserProfile(String userId, Integer age, String region, Integer incomeBracket) {
        this(userId, age, region, incomeBracket, List.of(), List.of(), List.of());
    }

    public UserProfile(
            String userId,
            Integer age,
            String region,
            Integer incomeBracket,
            List<String> householdTypes,
            List<String> specialConditions
    ) {
        this(userId, age, region, incomeBracket, householdTypes, specialConditions, List.of());
    }
}
