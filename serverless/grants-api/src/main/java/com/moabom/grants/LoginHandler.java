package com.moabom.grants;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

public final class LoginHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final AuthService auth;

    public LoginHandler() {
        this(AuthService.fromEnvironment());
    }

    public LoginHandler(AuthService auth) {
        this.auth = auth;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        if (event == null || !"POST".equals(event.getHttpMethod())) {
            return ApiResponses.error(405, "POST 메서드로 요청해 주세요.", true);
        }
        try {
            return ApiResponses.localDev(200, auth.login(event.getBody()));
        } catch (IllegalArgumentException ex) {
            return ApiResponses.error(400, ex.getMessage(), true);
        } catch (AuthService.AuthenticationFailure ex) {
            return ApiResponses.error(401, ex.getMessage(), true);
        } catch (RuntimeException ex) {
            return ApiResponses.error(500, "로그인하지 못했습니다.", true);
        }
    }
}
