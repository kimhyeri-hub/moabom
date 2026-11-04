package com.moabom.grants;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Local-only import of policy rows. This handler is not registered in the SAM template.
 */
public final class PolicyImportHandler {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final InMemoryGrantsRepository grants;

    public PolicyImportHandler(InMemoryGrantsRepository grants) {
        this.grants = grants;
    }

    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String body = event == null || event.getBody() == null ? "" : event.getBody();
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (Exception ex) {
            return ApiResponses.error(400, "JSON을 읽을 수 없습니다. CSV는 {\"format\":\"csv\",\"content\":\"...\"} 형식입니다.", true);
        }
        if (root == null || !root.isObject()) {
            return ApiResponses.error(400, "본문은 format 필드를 가진 JSON 객체여야 합니다.", true);
        }
        String format = root.path("format").asText("");
        PolicyImportResult result;
        try {
            if ("json".equalsIgnoreCase(format)) {
                JsonNode records = root.get("records");
                if (records == null || !records.isArray()) {
                    return ApiResponses.error(400, "json 형식은 records 배열이 필요합니다.", true);
                }
                result = PolicyImporter.importJson(records, grants.listAllGrants());
            } else if ("csv".equalsIgnoreCase(format)) {
                if (!root.path("content").isTextual()) {
                    return ApiResponses.error(400, "csv 형식은 content 문자열이 필요합니다.", true);
                }
                result = PolicyImporter.importCsv(root.path("content").asText(), grants.listAllGrants());
            } else {
                return ApiResponses.error(400, "format은 json 또는 csv여야 합니다.", true);
            }
        } catch (IllegalArgumentException ex) {
            return ApiResponses.error(400, ex.getMessage(), true);
        }
        for (Grant grant : result.imported()) {
            grants.add(grant);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("importedCount", result.imported().size());
        response.put("importedIds", result.imported().stream().map(Grant::grantId).toList());
        response.put("errors", result.errors());
        response.put("warnings", result.warnings());
        response.put("duplicateGroups", result.duplicateGroups());
        long sampleCount = grants.listAllGrants().stream().filter(Grant::sample).count();
        response.put("sampleCount", sampleCount);
        response.put("catalogSize", grants.listAllGrants().size());
        response.put("localDevOnly", true);
        boolean rejectedInput = result.errors().stream().anyMatch(issue -> !"duplicate".equals(issue.field()));
        int status = result.imported().isEmpty() && rejectedInput ? 400 : 200;
        return ApiResponses.localDev(status, response);
    }
}
