package com.moabom.grants;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Local-development scrap removal. VIEW records are not deleted.
 * Do not expose this route on a public deployment without authentication.
 */
public class DeleteScrapHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final UserProfileRepository profiles;
    private final BehaviorRepository behaviors;
    private final AccessTokens tokens;

    public DeleteScrapHandler() {
        this(DynamoUserProfileRepository.fromEnvironment(), DynamoBehaviorRepository.fromEnvironment());
    }

    public DeleteScrapHandler(UserProfileRepository profiles, BehaviorRepository behaviors) {
        this(profiles, behaviors, AccessTokens.fromEnvironment());
    }

    public DeleteScrapHandler(UserProfileRepository profiles, BehaviorRepository behaviors, AccessTokens tokens) {
        this.profiles = profiles;
        this.behaviors = behaviors;
        this.tokens = tokens;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            String userId = ApiResponses.pathId(event);
            APIGatewayProxyResponseEvent denied = tokens.errorIfUnauthorized(event, userId);
            if (denied != null) {
                return denied;
            }
            String grantId = grantId(event == null ? null : event.getBody());
            if (profiles.getProfile(userId).isEmpty()) {
                return ApiResponses.error(404, "사용자를 찾을 수 없습니다.", true);
            }
            ScrapDeleteResult result = behaviors.deleteScrap(userId, grantId);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("userId", userId);
            body.put("type", BehaviorType.SCRAP.name());
            body.put("grantId", grantId);
            body.put("deleted", result.deleted());
            body.put("localDevOnly", true);
            return ApiResponses.okLocalDev(body);
        } catch (IllegalArgumentException ex) {
            return ApiResponses.error(400, ex.getMessage(), true);
        } catch (RuntimeException ex) {
            logFailure(context);
            return ApiResponses.error(500, "스크랩을 취소하지 못했습니다.", true);
        }
    }

    private static String grantId(String body) {
        BehaviorRequestParser.BehaviorCommand command = BehaviorRequestParser.parse(body);
        if (command.type() != BehaviorType.SCRAP) {
            throw new IllegalArgumentException("스크랩만 취소할 수 있습니다. 조회 기록은 유지됩니다.");
        }
        return command.grantId();
    }

    private static void logFailure(Context context) {
        if (context != null && context.getLogger() != null) {
            context.getLogger().log("DELETE /users/{id}/events failed");
        }
    }
}
