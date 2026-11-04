package com.moabom.grants;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public final class LoginIdHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final AuthService auth;

    public LoginIdHandler() {
        this(AuthService.fromEnvironment());
    }

    public LoginIdHandler(AuthService auth) {
        this.auth = auth;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        if (event == null || !"GET".equals(event.getHttpMethod())) {
            return ApiResponses.error(405, "GET 메서드로 요청해 주세요.", true);
        }
        try {
            String loginId = "";
            if (event.getQueryStringParameters() != null && event.getQueryStringParameters().get("loginId") != null) {
                loginId = URLDecoder.decode(event.getQueryStringParameters().get("loginId"), StandardCharsets.UTF_8);
            }
            return ApiResponses.localDev(200, auth.loginIdAvailability(loginId));
        } catch (IllegalArgumentException ex) {
            return ApiResponses.error(400, ex.getMessage(), true);
        } catch (RuntimeException ex) {
            return ApiResponses.error(500, "아이디를 확인하지 못했습니다.", true);
        }
    }
}
