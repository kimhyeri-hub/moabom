package com.moabom.grants;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GrantMapper {
    private GrantMapper() {
    }

    public static Grant fromItem(Map<String, AttributeValue> item) {
        boolean sample = booleanAttr(item, GrantAttributes.SAMPLE);
        return new Grant(
                stringAttr(item, GrantAttributes.GRANT_ID),
                stringAttr(item, GrantAttributes.CATEGORY_TITLE),
                stringAttr(item, GrantAttributes.TITLE),
                stringAttr(item, GrantAttributes.DESCRIPTION),
                stringAttr(item, GrantAttributes.END_DATE),
                stringAttr(item, GrantAttributes.LINK),
                new Eligibility(
                        stringAttr(item, GrantAttributes.TARGET_AGE_GROUP),
                        numberAttr(item, GrantAttributes.MIN_AGE),
                        numberAttr(item, GrantAttributes.MAX_AGE),
                        numberAttr(item, GrantAttributes.MIN_INCOME_BRACKET),
                        numberAttr(item, GrantAttributes.MAX_INCOME_BRACKET),
                        stringAttr(item, GrantAttributes.TARGET_REGION),
                        clarityAttr(item, GrantAttributes.HOUSEHOLD_CLARITY),
                        stringList(item, GrantAttributes.HOUSEHOLD_TYPES),
                        clarityAttr(item, GrantAttributes.SPECIAL_CLARITY),
                        stringList(item, GrantAttributes.SPECIAL_CONDITIONS)
                ),
                sample,
                keywords(item),
                stringAttr(item, GrantAttributes.SOURCE_URL),
                stringAttr(item, GrantAttributes.REFERENCE_DATE),
                stringAttr(item, GrantAttributes.REFERENCE_TEXT),
                stringAttr(item, GrantAttributes.APPLICATION_START),
                stringAttr(item, GrantAttributes.SOURCE_SERVICE_ID)
        );
    }

    public static Map<String, AttributeValue> toItem(Grant grant) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(GrantAttributes.ENTITY_TYPE, AttributeValue.fromS(GrantAttributes.GRANT_PARTITION));
        putText(item, GrantAttributes.GRANT_ID, grant.grantId());
        putText(item, GrantAttributes.CATEGORY_TITLE, grant.categoryTitle());
        putText(item, GrantAttributes.TITLE, grant.title());
        putText(item, GrantAttributes.DESCRIPTION, grant.description());
        putText(item, GrantAttributes.END_DATE, grant.endDate());
        putText(item, GrantAttributes.LINK, grant.link());
        putText(item, GrantAttributes.SOURCE_URL, grant.sourceUrl());
        putText(item, GrantAttributes.REFERENCE_DATE, grant.referenceDate());
        putText(item, GrantAttributes.REFERENCE_TEXT, grant.referenceText());
        putText(item, GrantAttributes.APPLICATION_START, grant.applicationStart());
        putText(item, GrantAttributes.SOURCE_SERVICE_ID, grant.sourceServiceId());
        item.put(GrantAttributes.SAMPLE, AttributeValue.fromBool(grant.sample()));
        Eligibility eligibility = grant.eligibility();
        if (eligibility != null) {
            putText(item, GrantAttributes.TARGET_AGE_GROUP, eligibility.targetAgeGroup());
            putNumber(item, GrantAttributes.MIN_AGE, eligibility.minAge());
            putNumber(item, GrantAttributes.MAX_AGE, eligibility.maxAge());
            putNumber(item, GrantAttributes.MIN_INCOME_BRACKET, eligibility.minIncomeBracket());
            putNumber(item, GrantAttributes.MAX_INCOME_BRACKET, eligibility.maxIncomeBracket());
            putText(item, GrantAttributes.TARGET_REGION, eligibility.targetRegion());
            item.put(GrantAttributes.HOUSEHOLD_CLARITY, AttributeValue.fromS(eligibility.householdClarity().name()));
            item.put(GrantAttributes.SPECIAL_CLARITY, AttributeValue.fromS(eligibility.specialClarity().name()));
            putStrings(item, GrantAttributes.HOUSEHOLD_TYPES, eligibility.householdTypes());
            putStrings(item, GrantAttributes.SPECIAL_CONDITIONS, eligibility.specialConditions());
        }
        if (grant.keywords() != null && !grant.keywords().isEmpty()) {
            item.put(GrantAttributes.KEYWORDS, AttributeValue.fromL(
                    grant.keywords().stream().map(AttributeValue::fromS).toList()
            ));
        }
        return item;
    }

    private static List<String> keywords(Map<String, AttributeValue> item) {
        return stringList(item, GrantAttributes.KEYWORDS);
    }

    /**
     * A missing clarity attribute is UNKNOWN for sample rows and public rows.
     * UNRESTRICTED is kept only when that exact value was stored.
     */
    private static RequirementClarity clarityAttr(Map<String, AttributeValue> item, String name) {
        String value = stringAttr(item, name);
        if (value == null) {
            return RequirementClarity.UNKNOWN;
        }
        try {
            return RequirementClarity.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return RequirementClarity.UNKNOWN;
        }
    }

    private static List<String> stringList(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        if (value == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        if (value.l() != null) {
            value.l().forEach(entry -> {
                if (entry.s() != null) {
                    values.add(entry.s());
                }
            });
        } else if (value.ss() != null) {
            values.addAll(value.ss());
        }
        return values;
    }

    private static void putText(Map<String, AttributeValue> item, String name, String value) {
        if (value != null && !value.isBlank()) {
            item.put(name, AttributeValue.fromS(value));
        }
    }

    private static void putNumber(Map<String, AttributeValue> item, String name, Integer value) {
        if (value != null) {
            item.put(name, AttributeValue.fromN(Integer.toString(value)));
        }
    }

    private static void putStrings(Map<String, AttributeValue> item, String name, List<String> values) {
        if (values != null && !values.isEmpty()) {
            item.put(name, AttributeValue.fromSs(values));
        }
    }

    private static String stringAttr(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        if (value == null || value.s() == null || value.nul() != null && value.nul()) {
            return null;
        }
        return value.s();
    }

    private static Integer numberAttr(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        if (value == null || value.n() == null || value.n().isBlank()) {
            return null;
        }
        return Integer.valueOf(value.n());
    }

    private static boolean booleanAttr(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        return value != null && value.bool() != null && value.bool();
    }
}
