package com.moabom.grants;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Local-development profile read. Do not expose this route on a public deployment without authentication.
 */
public class GetProfileHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final UserProfileRepository profiles;
    private final AccessTokens tokens;

    public GetProfileHandler() {
        this(DynamoUserProfileRepository.fromEnvironment());
    }

    public GetProfileHandler(UserProfileRepository profiles) {
        this(profiles, AccessTokens.fromEnvironment());
    }

    public GetProfileHandler(UserProfileRepository profiles, AccessTokens tokens) {
        this.profiles = profiles;
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
            return profiles.getProfile(userId)
                    .map(profile -> ApiResponses.okLocalDev(profileBody(profile)))
                    .orElseGet(() -> ApiResponses.error(404, "사용자를 찾을 수 없습니다.", true));
        } catch (RuntimeException ex) {
            logFailure(context);
            return ApiResponses.error(500, "사용자 프로필을 조회하지 못했습니다.", true);
        }
    }

    static Map<String, Object> profileBody(UserProfile profile) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", profile.userId());
        body.put("age", profile.age());
        body.put("region", profile.region());
        body.put("incomeBracket", profile.incomeBracket());
        body.put("householdTypes", profile.householdTypes());
        body.put("specialConditions", profile.specialConditions());
        body.put("localDevOnly", true);
        return body;
    }

    private static void logFailure(Context context) {
        if (context != null && context.getLogger() != null) {
            context.getLogger().log("GET /users/{id}/profile failed");
        }
    }
}
