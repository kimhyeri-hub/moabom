package com.moabom.grants;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Local-development behavior history. Do not expose this route on a public deployment without authentication.
 */
public class ListBehaviorsHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final UserProfileRepository profiles;
    private final BehaviorRepository behaviors;
    private final AccessTokens tokens;

    public ListBehaviorsHandler() {
        this(DynamoUserProfileRepository.fromEnvironment(), DynamoBehaviorRepository.fromEnvironment());
    }

    public ListBehaviorsHandler(UserProfileRepository profiles, BehaviorRepository behaviors) {
        this(profiles, behaviors, AccessTokens.fromEnvironment());
    }

    public ListBehaviorsHandler(UserProfileRepository profiles, BehaviorRepository behaviors, AccessTokens tokens) {
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
            if (profiles.getProfile(userId).isEmpty()) {
                return ApiResponses.error(404, "사용자를 찾을 수 없습니다.", true);
            }
            List<UserBehavior> history = behaviors.listByUser(userId);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("userId", userId);
            body.put("localDevOnly", true);
            body.put("behaviors", history);
            return ApiResponses.okLocalDev(body);
        } catch (RuntimeException ex) {
            logFailure(context);
            return ApiResponses.error(500, "행동 이력을 조회하지 못했습니다.", true);
        }
    }

    private static void logFailure(Context context) {
        if (context != null && context.getLogger() != null) {
            context.getLogger().log("GET /users/{id}/behaviors failed");
        }
    }
}
