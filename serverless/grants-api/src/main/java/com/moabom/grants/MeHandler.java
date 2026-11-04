package com.moabom.grants;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

public final class MeHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final AuthService auth;
    private final AccessTokens tokens;

    public MeHandler() {
        this(AuthService.fromEnvironment(), AccessTokens.fromEnvironment());
    }

    public MeHandler(AuthService auth) {
        this(auth, null);
    }

    public MeHandler(AuthService auth, AccessTokens tokens) {
        this.auth = auth;
        this.tokens = tokens;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        if (event == null || (!"GET".equals(event.getHttpMethod()) && !"PATCH".equals(event.getHttpMethod()))) {
            return ApiResponses.error(405, "GET 또는 PATCH 메서드로 요청해 주세요.", true);
        }
        AccessTokens activeTokens = tokens == null ? null : tokens;
        if (activeTokens == null) {
            return ApiResponses.error(401, "인증 정보가 없습니다.", true);
        }
        APIGatewayProxyResponseEvent missing = activeTokens.errorIfNoSession(event);
        if (missing != null) {
            return missing;
        }
        try {
            String userId = activeTokens.verifiedSubject(event);
            if ("PATCH".equals(event.getHttpMethod())) {
                return ApiResponses.localDev(200, auth.update(userId, event.getBody()));
            }
            return ApiResponses.localDev(200, auth.currentUser(userId));
        } catch (IllegalArgumentException ex) {
            return ApiResponses.error(400, ex.getMessage(), true);
        } catch (AccountConflictException ex) {
            return ApiResponses.error(409, ex.getMessage(), true);
        } catch (AuthService.AuthenticationFailure ex) {
            return ApiResponses.error(401, ex.getMessage(), true);
        } catch (RuntimeException ex) {
            return ApiResponses.error(500, "사용자 정보를 처리하지 못했습니다.", true);
        }
    }
}
