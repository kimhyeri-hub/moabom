package com.moabom.grants;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

/**
 * Local-development profile write. Do not expose this route on a public deployment without authentication.
 */
public class PutProfileHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final UserProfileRepository profiles;
    private final AccessTokens tokens;

    public PutProfileHandler() {
        this(DynamoUserProfileRepository.fromEnvironment());
    }

    public PutProfileHandler(UserProfileRepository profiles) {
        this(profiles, AccessTokens.fromEnvironment());
    }

    public PutProfileHandler(UserProfileRepository profiles, AccessTokens tokens) {
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
            boolean existed = profiles.getProfile(userId).isPresent();
            UserProfile profile = ProfileValidator.parse(userId, event == null ? null : event.getBody());
            profiles.saveProfile(profile);
            MapBody body = new MapBody(profile, !existed);
            return ApiResponses.localDev(existed ? 200 : 201, body.asMap());
        } catch (IllegalArgumentException ex) {
            return ApiResponses.error(400, ex.getMessage(), true);
        } catch (RuntimeException ex) {
            logFailure(context);
            return ApiResponses.error(500, "사용자 프로필을 저장하지 못했습니다.", true);
        }
    }

    private record MapBody(UserProfile profile, boolean created) {
        private java.util.Map<String, Object> asMap() {
            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("userId", profile.userId());
            body.put("age", profile.age());
            body.put("region", profile.region());
            body.put("incomeBracket", profile.incomeBracket());
        body.put("householdTypes", profile.householdTypes());
        body.put("specialConditions", profile.specialConditions());
            body.put("interests", profile.interests());
            body.put("created", created);
            body.put("localDevOnly", true);
            return body;
        }
    }

    private static void logFailure(Context context) {
        if (context != null && context.getLogger() != null) {
            context.getLogger().log("PUT /users/{id}/profile failed");
        }
    }
}
