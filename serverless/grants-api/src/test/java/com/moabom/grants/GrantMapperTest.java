package com.moabom.grants;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrantMapperTest {
    @Test
    void mapsFlatDynamoItemToGrantAndEligibility() {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(GrantAttributes.ENTITY_TYPE, AttributeValue.fromS(GrantAttributes.GRANT_PARTITION));
        item.put(GrantAttributes.GRANT_ID, AttributeValue.fromS("example-001-youth-transport"));
        item.put(GrantAttributes.CATEGORY_TITLE, AttributeValue.fromS("예시 카테고리"));
        item.put(GrantAttributes.TITLE, AttributeValue.fromS("[예시] 모아봄 테스트용 청년 교통비"));
        item.put(GrantAttributes.DESCRIPTION, AttributeValue.fromS("실제 정책이 아닙니다."));
        item.put(GrantAttributes.END_DATE, AttributeValue.fromNul(true));
        item.put(GrantAttributes.LINK, AttributeValue.fromS("https://example.com/moabom/grants/example-001-youth-transport"));
        item.put(GrantAttributes.TARGET_AGE_GROUP, AttributeValue.fromS("청년"));
        item.put(GrantAttributes.MIN_AGE, AttributeValue.fromN("19"));
        item.put(GrantAttributes.MAX_AGE, AttributeValue.fromN("34"));
        item.put(GrantAttributes.MIN_INCOME_BRACKET, AttributeValue.fromN("1"));
        item.put(GrantAttributes.MAX_INCOME_BRACKET, AttributeValue.fromN("5"));
        item.put(GrantAttributes.TARGET_REGION, AttributeValue.fromS("예시특별시"));
        item.put(GrantAttributes.SAMPLE, AttributeValue.fromBool(true));

        Grant grant = GrantMapper.fromItem(item);

        assertEquals("example-001-youth-transport", grant.grantId());
        assertNull(grant.endDate());
        assertTrue(grant.sample());
        assertEquals(19, grant.eligibility().minAge());
        assertEquals(34, grant.eligibility().maxAge());
        assertEquals(1, grant.eligibility().minIncomeBracket());
        assertEquals(5, grant.eligibility().maxIncomeBracket());
        assertEquals("예시특별시", grant.eligibility().targetRegion());
        assertEquals("청년", grant.eligibility().targetAgeGroup());
        assertEquals(List.of(), grant.keywords());
    }
}
