package com.moabom.grants;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Local-development behavior write. The same user, action, and grant are stored once.
 * Do not expose this route on a public deployment without authentication.
 */
public class PostBehaviorHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final GrantsRepository grants;
    private final UserProfileRepository profiles;
    private final BehaviorRepository behaviors;
    private final AccessTokens tokens;

    public PostBehaviorHandler() {
        this(
                DynamoGrantsRepository.fromEnvironment(),
                DynamoUserProfileRepository.fromEnvironment(),
                DynamoBehaviorRepository.fromEnvironment()
        );
    }

    public PostBehaviorHandler(GrantsRepository grants, UserProfileRepository profiles, BehaviorRepository behaviors) {
        this(grants, profiles, behaviors, AccessTokens.fromEnvironment());
    }

    public PostBehaviorHandler(
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
            BehaviorRequestParser.BehaviorCommand command = BehaviorRequestParser.parse(
                    event == null ? null : event.getBody());
            if (profiles.getProfile(userId).isEmpty()) {
                return ApiResponses.error(404, "사용자를 찾을 수 없습니다.", true);
            }
            if (grants.getGrant(command.grantId()).isEmpty()) {
                return ApiResponses.error(404, "지원금을 찾을 수 없습니다.", true);
            }
            BehaviorWriteResult result = behaviors.saveBehavior(new UserBehavior(
                    userId,
                    command.type(),
                    command.grantId(),
                    Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
            ));
            return ApiResponses.localDev(result.created() ? 201 : 200, eventBody(result));
        } catch (IllegalArgumentException ex) {
            return ApiResponses.error(400, ex.getMessage(), true);
        } catch (RuntimeException ex) {
            logFailure(context);
            return ApiResponses.error(500, "행동 기록을 저장하지 못했습니다.", true);
        }
    }

    private static Map<String, Object> eventBody(BehaviorWriteResult result) {
        UserBehavior behavior = result.behavior();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", behavior.userId());
        body.put("type", behavior.type().name());
        body.put("grantId", behavior.grantId());
        body.put("occurredAt", behavior.occurredAt());
        body.put("duplicate", !result.created());
        body.put("localDevOnly", true);
        return body;
    }

    private static void logFailure(Context context) {
        if (context != null && context.getLogger() != null) {
            context.getLogger().log("POST /users/{id}/events failed");
        }
    }
}
