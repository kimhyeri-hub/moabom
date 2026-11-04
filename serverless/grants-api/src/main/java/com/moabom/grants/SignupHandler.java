package com.moabom.grants;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

public final class SignupHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final AuthService auth;

    public SignupHandler() {
        this(AuthService.fromEnvironment());
    }

    public SignupHandler(AuthService auth) {
        this.auth = auth;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        if (event == null || !"POST".equals(event.getHttpMethod())) {
            return ApiResponses.error(405, "POST 메서드로 요청해 주세요.", true);
        }
        try {
            return ApiResponses.localDev(201, auth.signup(event.getBody()));
        } catch (IllegalArgumentException ex) {
            return ApiResponses.error(400, ex.getMessage(), true);
        } catch (AccountConflictException ex) {
            return ApiResponses.error(409, ex.getMessage(), true);
        } catch (RuntimeException ex) {
            return ApiResponses.error(500, "회원가입을 완료하지 못했습니다.", true);
        }
    }
}
