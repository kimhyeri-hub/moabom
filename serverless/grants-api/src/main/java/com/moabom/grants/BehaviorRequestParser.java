package com.moabom.grants;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class BehaviorRequestParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BehaviorRequestParser() {
    }

    public static BehaviorCommand parse(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("요청 본문이 올바른 JSON 객체여야 합니다.");
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("요청 본문이 올바른 JSON 객체여야 합니다.");
        }
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("요청 본문이 올바른 JSON 객체여야 합니다.");
        }
        JsonNode typeNode = node.get("type");
        if (typeNode == null || !typeNode.isTextual()) {
            throw new IllegalArgumentException("행동 유형은 VIEW 또는 SCRAP이어야 합니다.");
        }
        BehaviorType type;
        try {
            type = BehaviorType.valueOf(typeNode.asText());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("행동 유형은 VIEW 또는 SCRAP이어야 합니다.");
        }
        JsonNode grantNode = node.get("grantId");
        if (grantNode == null || !grantNode.isTextual() || !ApiResponses.ID.matcher(grantNode.asText()).matches()) {
            throw new IllegalArgumentException("지원금 ID 형식이 올바르지 않습니다.");
        }
        return new BehaviorCommand(type, grantNode.asText());
    }

    public record BehaviorCommand(BehaviorType type, String grantId) {
    }
}
