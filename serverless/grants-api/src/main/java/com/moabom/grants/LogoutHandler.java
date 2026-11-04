package com.moabom.grants;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.util.Map;

public final class LogoutHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final AccessTokens tokens;

    public LogoutHandler() {
        this(AccessTokens.fromEnvironment());
    }

    public LogoutHandler(AccessTokens tokens) {
        this.tokens = tokens;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        if (event == null || !"POST".equals(event.getHttpMethod())) {
            return ApiResponses.error(405, "POST 메서드로 요청해 주세요.", true);
        }
        APIGatewayProxyResponseEvent missing = tokens.errorIfNoSession(event);
        if (missing != null) {
            return missing;
        }
        try {
            tokens.revokeBearer(event);
            return ApiResponses.localDev(200, Map.of(
                    "message", "로그아웃했습니다. 이 토큰은 만료 시각까지 다시 사용할 수 없습니다.",
                    "localDevOnly", true
            ));
        } catch (IllegalArgumentException ex) {
            return ApiResponses.error(401, "인증 정보가 유효하지 않습니다.", true);
        } catch (IllegalStateException ex) {
            return ApiResponses.error(500, "로그아웃 상태를 저장하지 못했습니다.", true);
        }
    }
}
