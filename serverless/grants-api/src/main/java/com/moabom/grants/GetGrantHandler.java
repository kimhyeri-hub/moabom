package com.moabom.grants;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

public class GetGrantHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final GrantsRepository grants;

    public GetGrantHandler() {
        this(DynamoGrantsRepository.fromEnvironment());
    }

    public GetGrantHandler(GrantsRepository grants) {
        this.grants = grants;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            String grantId = ApiResponses.pathId(event);
            if (grantId == null || !ApiResponses.ID.matcher(grantId).matches()) {
                return ApiResponses.error(400, "지원금 ID 형식이 올바르지 않습니다.", false);
            }
            return grants.getGrant(grantId)
                    .filter(grant -> !grant.sample())
                    .map(ApiResponses::ok)
                    .orElseGet(() -> ApiResponses.error(404, "지원금을 찾을 수 없습니다.", false));
        } catch (RuntimeException ex) {
            logFailure(context);
            return ApiResponses.error(500, "지원금을 조회하지 못했습니다.", false);
        }
    }

    private static void logFailure(Context context) {
        if (context != null && context.getLogger() != null) {
            context.getLogger().log("GET /grants/{id} failed");
        }
    }
}
