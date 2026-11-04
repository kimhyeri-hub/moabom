package com.moabom.grants;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moabom.grants.local.LocalApiServer;
import com.moabom.grants.local.LocalCatalog;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyClarityAndImportTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void missingClarityStaysUnknownAndExplicitUnrestrictedPasses() throws Exception {
        UserProfile user = new UserProfile("example-user", 30, "예시특별시", 3, List.of("SINGLE"), List.of("DISABLED"));
        Grant missing = GrantMapper.fromItem(baseItem(true));
        assertEquals(RequirementClarity.UNKNOWN, missing.eligibility().householdClarity());
        assertEquals(RequirementClarity.UNKNOWN, missing.eligibility().specialClarity());
        EligibilityAssessment unknown = EligibilityEvaluator.assess(user, missing.eligibility());
        assertEquals(EligibilityStatus.CHECK_REQUIRED, unknown.status());
        assertTrue(unknown.checks().stream().anyMatch(check ->
                "HOUSEHOLD".equals(check.dimension()) && check.result() == CheckResult.UNKNOWN));
        assertTrue(unknown.checks().stream().noneMatch(check -> check.detail().contains("제한 없음")));

        Map<String, AttributeValue> explicit = baseItem(false);
        explicit.put(GrantAttributes.HOUSEHOLD_CLARITY, AttributeValue.fromS("UNRESTRICTED"));
        explicit.put(GrantAttributes.SPECIAL_CLARITY, AttributeValue.fromS("UNRESTRICTED"));
        Grant open = GrantMapper.fromItem(explicit);
        EligibilityAssessment passed = EligibilityEvaluator.assess(user, open.eligibility());
        assertEquals(EligibilityStatus.ELIGIBLE, passed.status());
        assertTrue(passed.checks().stream().anyMatch(check ->
                "HOUSEHOLD".equals(check.dimension())
                        && check.result() == CheckResult.PASS
                        && check.detail().contains("제한이 없습니다")));

        List<Grant> samples = LocalCatalog.load();
        assertEquals(8, samples.size());
        assertTrue(samples.stream().allMatch(grant ->
                grant.sample()
                        && grant.eligibility().householdClarity() == RequirementClarity.UNRESTRICTED
                        && grant.eligibility().specialClarity() == RequirementClarity.UNRESTRICTED));
    }

    @Test
    void ineligibleGrantIsExcludedFromRecommendations() {
        UserProfile user = new UserProfile("example-user", 30, "예시특별시", 3, List.of("SINGLE"), List.of());
        Grant blocked = new Grant(
                "blocked-parent",
                "돌봄",
                "[예시] 한부모만",
                "실제 정책이 아닙니다.",
                "2026-12-31",
                "https://example.com/blocked",
                new Eligibility(
                        "청년",
                        19,
                        39,
                        1,
                        6,
                        "예시특별시",
                        RequirementClarity.SPECIFIED,
                        List.of("SINGLE_PARENT"),
                        RequirementClarity.UNRESTRICTED,
                        List.of()
                ),
                true,
                List.of("양육")
        );
        Grant open = new Grant(
                "open-youth",
                "교통",
                "[예시] 제한 없음",
                "실제 정책이 아닙니다.",
                "2026-12-31",
                "https://example.com/open",
                new Eligibility("청년", 19, 39, 1, 6, "예시특별시"),
                true,
                List.of("교통")
        );
        assertEquals(EligibilityStatus.INELIGIBLE, EligibilityEvaluator.assess(user, blocked.eligibility()).status());
        List<String> ids = RecommendationScorer.recommend(user, List.of(blocked, open), List.of()).stream()
                .map(item -> item.grant().grantId())
                .toList();
        assertEquals(List.of("open-youth"), ids);
        assertEquals(0.6, RecommendationScorer.CONTENT_WEIGHT);
        assertEquals(0.4, RecommendationScorer.BEHAVIOR_WEIGHT);
    }

    @Test
    void jsonAndCsvImportKeepSamplesAndReportErrors() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> PublicPolicyMapper.toGrant(new PublicPolicyRecord(
                "수동입력",
                "WLF-ERR",
                "기준일 오류",
                null,
                null,
                "2026",
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
                null,
                List.of(),
                false
        )));

        List<Grant> existing = LocalCatalog.load();
        String json = """
                [
                  {
                    "sourceName": "수동입력",
                    "serviceId": "WLF-200",
                    "serviceName": "공개 입력 예시",
                    "summary": "외부 API를 호출하지 않고 입력한 행입니다.",
                    "serviceUrl": "https://example.com/policies/wlf-200",
                    "applicationLink": "https://example.com/apply/wlf-200",
                    "referenceDate": "2026-11-04",
                    "referenceText": "2026",
                    "applicationStart": "2026-11-01",
                    "applicationEnd": "2026-12-31",
                    "regionText": "예시특별시",
                    "minAge": 19,
                    "maxAge": 39,
                    "minIncomeBracket": 1,
                    "maxIncomeBracket": 6,
                    "householdClarity": "UNRESTRICTED",
                    "specialClarity": "UNRESTRICTED",
                    "sample": false
                  },
                  {"serviceId": "WLF-201", "summary": "이름이 없습니다."},
                  {"serviceId": "WLF-202", "serviceName": "기준일 오류", "referenceDate": "2026"},
                  {
                    "sourceName": "수동입력",
                    "serviceId": "WLF-200",
                    "serviceName": "다른 이름",
                    "serviceUrl": "https://example.com/policies/duplicate"
                  },
                  {
                    "serviceId": "WLF-203",
                    "serviceName": "코드 오류",
                    "householdClarity": "SPECIFIED",
                    "householdTypes": ["DISABLED"]
                  },
                  {
                    "serviceId": "WLF-204",
                    "serviceName": "정보 없음 정책",
                    "serviceUrl": "https://example.com/policies/wlf-204",
                    "referenceDate": "2026-11-04",
                    "sample": false
                  }
                ]
                """;
        PolicyImportResult imported = PolicyImporter.importJson(MAPPER.readTree(json), existing);
        assertEquals(8, existing.size());
        assertEquals(List.of("policy-wlf-200", "policy-wlf-204"), imported.imported().stream().map(Grant::grantId).toList());
        Grant stored = imported.imported().get(0);
        assertFalse(stored.sample());
        assertEquals("https://example.com/policies/wlf-200", stored.sourceUrl());
        assertEquals("https://example.com/apply/wlf-200", stored.link());
        assertEquals("2026-11-04", stored.referenceDate());
        assertEquals("2026", stored.referenceText());
        assertEquals("2026-11-01", stored.applicationStart());
        assertEquals("2026-12-31", stored.endDate());
        assertEquals("수동입력:WLF-200", stored.sourceServiceId());
        assertEquals(RequirementClarity.UNRESTRICTED, stored.eligibility().householdClarity());
        assertTrue(imported.imported().stream().noneMatch(grant -> "2026-01-01".equals(grant.referenceDate())));
        assertTrue(imported.errors().stream().anyMatch(issue -> "serviceName".equals(issue.field()) && issue.message().contains("서비스명")));
        assertTrue(imported.errors().stream().anyMatch(issue -> "referenceDate".equals(issue.field()) && issue.message().contains("yyyy-MM-dd")));
        assertTrue(imported.errors().stream().anyMatch(issue -> "householdTypes".equals(issue.field()) && issue.message().contains("DISABLED")));
        assertTrue(imported.errors().stream().anyMatch(issue -> "duplicate".equals(issue.field())));
        assertEquals(List.of("policy-wlf-200", "policy-wlf-200"), imported.duplicateGroups().get(0));

        Grant unclear = imported.imported().get(1);
        assertFalse(unclear.sample());
        assertNull(unclear.eligibility().minAge());
        assertNull(unclear.eligibility().targetRegion());
        assertEquals(RequirementClarity.UNKNOWN, unclear.eligibility().householdClarity());
        UserProfile user = new UserProfile("example-user", 30, "예시특별시", 3, List.of("SINGLE"), List.of("DISABLED"));
        assertEquals(EligibilityStatus.CHECK_REQUIRED, EligibilityEvaluator.assess(user, unclear.eligibility()).status());

        String csv = """
                serviceId,serviceName,serviceUrl,referenceDate,sourceName,sample
                WLF-300,CSV 정책,https://example.com/policies/wlf-300,2026-11-04,수동입력,false
                ,이름만,https://example.com/missing,,수동입력,false
                WLF-300,중복,https://example.com/policies/wlf-300,2026-11-04,수동입력,false
                """;
        PolicyImportResult csvResult = PolicyImporter.importCsv(csv, existing);
        assertEquals(1, csvResult.imported().size());
        Grant csvGrant = csvResult.imported().get(0);
        assertEquals("policy-wlf-300", csvGrant.grantId());
        assertFalse(csvGrant.sample());
        assertEquals("2026-11-04", csvGrant.referenceDate());
        assertEquals("https://example.com/policies/wlf-300", csvGrant.sourceUrl());
        assertTrue(csvResult.errors().stream().anyMatch(issue -> issue.row() == 3 && "serviceId".equals(issue.field())));
        assertTrue(csvResult.errors().stream().anyMatch(issue -> issue.row() == 4 && "duplicate".equals(issue.field())));
        assertEquals(8, existing.stream().filter(Grant::sample).count());
    }

    @Test
    void localImportEndpointAddsPolicyWithoutRemovingSamples() throws Exception {
        LocalApiServer server = LocalApiServer.start(0);
        try {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + server.port();
            HttpResponse<String> created = client.send(request(base, """
                    {"format":"json","records":[{
                      "sourceName":"수동입력",
                      "serviceId":"WLF-500",
                      "serviceName":"로컬 입력 정책",
                      "serviceUrl":"https://example.com/policies/wlf-500",
                      "applicationLink":"https://example.com/apply/wlf-500",
                      "referenceDate":"2026-11-04",
                      "sample":false
                    }]}
                    """), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, created.statusCode());
            assertEquals(1, MAPPER.readTree(created.body()).get("importedCount").asInt());
            assertEquals(8, MAPPER.readTree(created.body()).get("sampleCount").asInt());

            HttpResponse<String> grant = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/grants/policy-wlf-500")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, grant.statusCode());
            var body = MAPPER.readTree(grant.body());
            assertFalse(body.get("sample").asBoolean());
            assertEquals("https://example.com/policies/wlf-500", body.get("sourceUrl").asText());
            assertEquals("https://example.com/apply/wlf-500", body.get("link").asText());
            assertEquals("2026-11-04", body.get("referenceDate").asText());
            assertTrue(body.get("applicationStart").isNull());
            assertTrue(body.get("endDate").isNull());
            assertEquals("UNKNOWN", body.get("eligibility").get("householdClarity").asText());

            HttpResponse<String> grants = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/grants")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            int samples = 0;
            boolean imported = false;
            for (var item : MAPPER.readTree(grants.body()).get("grants")) {
                if (item.get("sample").asBoolean()) {
                    samples++;
                }
                if ("policy-wlf-500".equals(item.get("grantId").asText())) {
                    imported = true;
                }
            }
            assertEquals(0, samples);
            assertTrue(imported);
            assertEquals(13, MAPPER.readTree(grants.body()).get("grants").size());
        } finally {
            server.stop();
        }
    }

    private static HttpRequest request(String base, String body) {
        return HttpRequest.newBuilder(URI.create(base + "/policies/import"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private static Map<String, AttributeValue> baseItem(boolean sample) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(GrantAttributes.GRANT_ID, AttributeValue.fromS(sample ? "sample-missing-clarity" : "public-explicit"));
        item.put(GrantAttributes.TITLE, AttributeValue.fromS("구분 확인"));
        item.put(GrantAttributes.MIN_AGE, AttributeValue.fromN("19"));
        item.put(GrantAttributes.MAX_AGE, AttributeValue.fromN("39"));
        item.put(GrantAttributes.MIN_INCOME_BRACKET, AttributeValue.fromN("1"));
        item.put(GrantAttributes.MAX_INCOME_BRACKET, AttributeValue.fromN("6"));
        item.put(GrantAttributes.TARGET_REGION, AttributeValue.fromS("예시특별시"));
        item.put(GrantAttributes.SAMPLE, AttributeValue.fromBool(sample));
        return item;
    }
}
