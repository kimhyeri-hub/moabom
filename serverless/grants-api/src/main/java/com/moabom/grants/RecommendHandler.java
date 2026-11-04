package com.moabom.grants;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

/**
 * Local-development recommendations. Do not expose this route on a public deployment without authentication.
 */
public class RecommendHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final GrantsRepository grants;
    private final UserProfileRepository profiles;
    private final BehaviorRepository behaviors;
    private final AccessTokens tokens;

    public RecommendHandler() {
        this(
                DynamoGrantsRepository.fromEnvironment(),
                DynamoUserProfileRepository.fromEnvironment(),
                DynamoBehaviorRepository.fromEnvironment()
        );
    }

    public RecommendHandler(GrantsRepository grants, UserProfileRepository profiles, BehaviorRepository behaviors) {
        this(grants, profiles, behaviors, AccessTokens.fromEnvironment());
    }

    public RecommendHandler(
            GrantsRepository grants,
            UserProfileRepository profiles,
            BehaviorRepository behaviors,
            AccessTokens tokens) {
        this.grants = grants;
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
            UserProfile profile = profiles.getProfile(userId).orElse(null);
            if (profile == null) {
                return ApiResponses.error(404, "사용자를 찾을 수 없습니다.", true);
            }
            java.util.List<Grant> catalog = grants.listAllGrants().stream()
                    .filter(grant -> grant != null && !grant.sample())
                    .toList();
            RecommendationResponse response = new RecommendationResponse(
                    userId,
                    true,
                    ScoringPolicy.initial(),
                    RecommendationScorer.recommend(profile, catalog, behaviors.listByUser(userId))
            );
            return ApiResponses.okLocalDev(response);
        } catch (RuntimeException ex) {
            logFailure(context);
            return ApiResponses.error(500, "추천을 만들지 못했습니다.", true);
        }
    }

    private static void logFailure(Context context) {
        if (context != null && context.getLogger() != null) {
            context.getLogger().log("GET /users/{id}/recommendations failed");
        }
    }
}
