package com.moabom.grants;

/** DynamoDB attribute names for a grant item. */
public final class GrantAttributes {
    public static final String ENTITY_TYPE = "entityType";
    public static final String GRANT_PARTITION = "GRANT";
    public static final String GRANT_ID = "grantId";
    public static final String CATEGORY_TITLE = "categoryTitle";
    public static final String TITLE = "title";
    public static final String DESCRIPTION = "description";
    public static final String END_DATE = "endDate";
    public static final String LINK = "link";
    public static final String TARGET_AGE_GROUP = "targetAgeGroup";
    public static final String MIN_AGE = "minAge";
    public static final String MAX_AGE = "maxAge";
    public static final String MIN_INCOME_BRACKET = "minIncomeBracket";
    public static final String MAX_INCOME_BRACKET = "maxIncomeBracket";
    public static final String TARGET_REGION = "targetRegion";
    public static final String SAMPLE = "sample";
    public static final String KEYWORDS = "keywords";
    public static final String SOURCE_URL = "sourceUrl";
    public static final String REFERENCE_DATE = "referenceDate";
    public static final String REFERENCE_TEXT = "referenceText";
    public static final String APPLICATION_START = "applicationStart";
    public static final String SOURCE_SERVICE_ID = "sourceServiceId";
    public static final String HOUSEHOLD_CLARITY = "householdClarity";
    public static final String HOUSEHOLD_TYPES = "householdTypes";
    public static final String SPECIAL_CLARITY = "specialClarity";
    public static final String SPECIAL_CONDITIONS = "specialConditions";

    private GrantAttributes() {
    }
}
