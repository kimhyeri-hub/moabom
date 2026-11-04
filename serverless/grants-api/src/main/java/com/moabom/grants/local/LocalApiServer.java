package com.moabom.grants.local;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.moabom.grants.AccessTokens;
import com.moabom.grants.AccountRepository;
import com.moabom.grants.AuthService;
import com.moabom.grants.ChatHandler;
import com.moabom.grants.ChatPrompts;
import com.moabom.grants.DeleteScrapHandler;
import com.moabom.grants.InMemoryAccountRepository;
import com.moabom.grants.InMemoryTokenRevocationRepository;
import com.moabom.grants.LoginHandler;
import com.moabom.grants.LoginIdHandler;
import com.moabom.grants.LogoutHandler;
import com.moabom.grants.MeHandler;
import com.moabom.grants.PasswordHashes;
import com.moabom.grants.SignupHandler;
import com.moabom.grants.GetGrantHandler;
import com.moabom.grants.GetProfileHandler;
import com.moabom.grants.GrantsRepository;
import com.moabom.grants.InMemoryBehaviorRepository;
import com.moabom.grants.InMemoryGrantsRepository;
import com.moabom.grants.InMemoryUserProfileRepository;
import com.moabom.grants.ListBehaviorsHandler;
import com.moabom.grants.ListGrantsHandler;
import com.moabom.grants.PolicyImportHandler;
import com.moabom.grants.PostBehaviorHandler;
import com.moabom.grants.PutProfileHandler;
import com.moabom.grants.RecommendHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Development HTTP adapter. It calls the same Lambda handlers as AWS, but stores data in memory
 * and listens only on 127.0.0.1. This process is not AWS Lambda and does not contact AWS.
 */
public final class LocalApiServer {
    private static final Set<String> ALLOWED_ORIGINS = Set.of(
            "http://localhost:3000",
            "http://127.0.0.1:3000"
    );

    private final HttpServer server;
    private final ExecutorService workers;
    private final Object requestLock = new Object();
    private final ListGrantsHandler listGrants;
    private final GetGrantHandler getGrant;
    private final PutProfileHandler putProfile;
    private final GetProfileHandler getProfile;
    private final PostBehaviorHandler postBehavior;
    private final DeleteScrapHandler deleteScrap;
    private final ListBehaviorsHandler listBehaviors;
    private final RecommendHandler recommend;
    private final PolicyImportHandler importPolicies;
    private final SignupHandler signup;
    private final LoginHandler login;
    private final LogoutHandler logout;
    private final MeHandler me;
    private final LoginIdHandler loginId;
    private final ChatHandler chat;

    private LocalApiServer(HttpServer server, ExecutorService workers, InMemoryGrantsRepository grants, AccessTokens tokens) {
        this.server = server;
        this.workers = workers;
        InMemoryUserProfileRepository profiles = new InMemoryUserProfileRepository(new HashMap<>());
        InMemoryBehaviorRepository behaviors = new InMemoryBehaviorRepository(new ArrayList<>());
        AccessTokens sessionTokens = tokens.withRevocations(new InMemoryTokenRevocationRepository());
        this.listGrants = new ListGrantsHandler(grants);
        this.getGrant = new GetGrantHandler(grants);
        this.putProfile = new PutProfileHandler(profiles, sessionTokens);
        this.getProfile = new GetProfileHandler(profiles, sessionTokens);
        this.postBehavior = new PostBehaviorHandler(grants, profiles, behaviors, sessionTokens);
        this.deleteScrap = new DeleteScrapHandler(profiles, behaviors, sessionTokens);
        this.listBehaviors = new ListBehaviorsHandler(profiles, behaviors, sessionTokens);
        this.recommend = new RecommendHandler(grants, profiles, behaviors, sessionTokens);
        this.importPolicies = new PolicyImportHandler(grants);
        AccountRepository accounts = new InMemoryAccountRepository();
        AuthService auth = new AuthService(accounts, sessionTokens, new PasswordHashes());
        this.signup = new SignupHandler(auth);
        this.login = new LoginHandler(auth);
        this.logout = new LogoutHandler(sessionTokens);
        this.me = new MeHandler(auth, sessionTokens);
        this.loginId = new LoginIdHandler(auth);
        this.chat = new ChatHandler((systemPrompt, userMessage) -> {
            if (!ChatPrompts.SYSTEM.equals(systemPrompt)) {
                throw new IllegalStateException("moabom chat prompt changed");
            }
            return "로컬 확인용 응답입니다. OpenAI는 호출하지 않았습니다.";
        });
    }

    public static LocalApiServer start(int port) throws IOException {
        return start(port, AccessTokens.fromEnvironment());
    }

    static LocalApiServer start(int port, AccessTokens tokens) throws IOException {
        InMemoryGrantsRepository grants = new InMemoryGrantsRepository(LocalCatalog.loadAll());
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 0);
        ExecutorService workers = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "moabom-local-api");
            thread.setDaemon(true);
            return thread;
        });
        LocalApiServer api = new LocalApiServer(httpServer, workers, grants, tokens);
        httpServer.createContext("/", api::handle);
        httpServer.setExecutor(workers);
        httpServer.start();
        return api;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void stop() {
        server.stop(0);
        workers.shutdownNow();
    }

    public static void main(String[] args) throws IOException {
        int port = 8080;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } else if (System.getenv("LOCAL_API_PORT") != null) {
            port = Integer.parseInt(System.getenv("LOCAL_API_PORT"));
        }
        LocalApiServer api = start(port);
        System.out.println("MOABOM local API is running. This is not AWS Lambda and it does not call AWS.");
        System.out.println("Bound only to http://127.0.0.1:" + api.port());
    }

    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        URI uri = exchange.getRequestURI();
        String path = normalize(uri.getPath());
        if ("OPTIONS".equals(method)) {
            write(exchange, 204, "", corsHeaders(exchange, true));
            return;
        }
        try {
            APIGatewayProxyResponseEvent response;
            synchronized (requestLock) {
                response = dispatch(method, path, query(uri.getRawQuery()), readBody(exchange), requestHeaders(exchange));
            }
            if (response == null) {
                write(exchange, 404, "{\"message\":\"요청한 경로가 없습니다.\"}", corsHeaders(exchange, false));
                return;
            }
            Map<String, String> headers = new HashMap<>();
            if (response.getHeaders() != null) {
                headers.putAll(response.getHeaders());
            }
            headers.putAll(corsHeaders(exchange, false));
            write(exchange, response.getStatusCode(), response.getBody() == null ? "" : response.getBody(), headers);
        } catch (RuntimeException ex) {
            write(exchange, 500, "{\"message\":\"로컬 API 처리에 실패했습니다.\"}", corsHeaders(exchange, false));
        }
    }

    private static Map<String, String> requestHeaders(HttpExchange exchange) {
        Map<String, String> requestHeaders = new HashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> {
            if (values != null && !values.isEmpty()) {
                requestHeaders.put(name, values.get(0));
            }
        });
        return requestHeaders;
    }

    private APIGatewayProxyResponseEvent dispatch(
            String method,
            String path,
            Map<String, String> query,
            String body,
            Map<String, String> requestHeaders) {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod(method)
                .withPath(path)
                .withBody(body)
                .withHeaders(requestHeaders)
                .withQueryStringParameters(query.isEmpty() ? null : query);
        if ("POST".equals(method) && "/auth/signup".equals(path)) {
            return signup.handleRequest(event, null);
        }
        if ("POST".equals(method) && "/auth/login".equals(path)) {
            return login.handleRequest(event, null);
        }
        if ("POST".equals(method) && "/auth/logout".equals(path)) {
            return logout.handleRequest(event, null);
        }
        if ("/auth/me".equals(path) && ("GET".equals(method) || "PATCH".equals(method))) {
            return me.handleRequest(event, null);
        }
        if ("GET".equals(method) && "/auth/login-id".equals(path)) {
            return loginId.handleRequest(event, null);
        }
        if ("POST".equals(method) && "/chat".equals(path)) {
            return chat.handleRequest(event, null);
        }
        if ("POST".equals(method) && "/policies/import".equals(path)) {
            return importPolicies.handleRequest(event, null);
        }
        if ("GET".equals(method) && "/grants".equals(path)) {
            return listGrants.handleRequest(event, null);
        }
        if ("GET".equals(method) && path.startsWith("/grants/")) {
            event.setPathParameters(Map.of("id", path.substring("/grants/".length())));
            return getGrant.handleRequest(event, null);
        }
        String[] parts = path.split("/");
        if (parts.length == 4 && "users".equals(parts[1])) {
            event.setPathParameters(Map.of("id", parts[2]));
            String action = parts[3];
            if ("profile".equals(action) && "PUT".equals(method)) {
                return putProfile.handleRequest(event, null);
            }
            if ("profile".equals(action) && "GET".equals(method)) {
                return getProfile.handleRequest(event, null);
            }
            if ("events".equals(action) && "POST".equals(method)) {
                return postBehavior.handleRequest(event, null);
            }
            if ("events".equals(action) && "DELETE".equals(method)) {
                return deleteScrap.handleRequest(event, null);
            }
            if ("behaviors".equals(action) && "GET".equals(method)) {
                return listBehaviors.handleRequest(event, null);
            }
            if ("recommendations".equals(action) && "GET".equals(method)) {
                return recommend.handleRequest(event, null);
            }
        }
        return null;
    }

    private static String normalize(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String normalized = path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
        return normalized;
    }

    private static Map<String, String> query(String rawQuery) {
        Map<String, String> query = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return query;
        }
        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            if (separator < 0) {
                query.put(pair, "");
            } else {
                query.put(pair.substring(0, separator), pair.substring(separator + 1));
            }
        }
        return query;
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static Map<String, String> corsHeaders(HttpExchange exchange, boolean preflight) {
        Map<String, String> headers = new HashMap<>();
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && ALLOWED_ORIGINS.contains(origin)) {
            headers.put("Access-Control-Allow-Origin", origin);
            headers.put("Vary", "Origin");
        }
        if (preflight) {
            headers.put("Access-Control-Allow-Methods", "GET,PUT,POST,DELETE,OPTIONS");
            headers.put("Access-Control-Allow-Headers", "Content-Type, Authorization");
        }
        headers.put("Content-Type", "application/json; charset=utf-8");
        return headers;
    }

    private static void write(HttpExchange exchange, int status, String body, Map<String, String> headers) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        headers.forEach((name, value) -> exchange.getResponseHeaders().set(name, value));
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
