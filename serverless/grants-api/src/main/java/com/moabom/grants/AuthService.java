package com.moabom.grants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class AuthService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern LOGIN_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{3,49}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private final AccountRepository accounts;
    private final AccessTokens tokens;
    private final PasswordHashes passwords;

    public AuthService(AccountRepository accounts, AccessTokens tokens, PasswordHashes passwords) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.passwords = passwords;
    }

    public static AuthService fromEnvironment() {
        return new AuthService(
                DynamoAccountRepository.fromEnvironment(),
                AccessTokens.fromEnvironment(),
                new PasswordHashes()
        );
    }

    public Map<String, Object> signup(String body) {
        JsonNode payload = object(body);
        String loginId = loginId(payload.get("loginId"));
        String email = email(payload.get("email"));
        String name = name(payload.get("name"));
        String password = required(payload.get("password"), "비밀번호");
        String birth = birth(payload.get("dateOfBirth"));
        String region = optionalRegion(payload.get("region"));
        Integer income = optionalIncome(payload.get("incomeBracket"));
        validatePassword(password, loginId);
        Account draft = new Account(null, loginId, email, name, birth, passwords.hash(password), region, income);
        Account stored = accounts.insertNew(draft);
        return session(stored);
    }

    public Map<String, Object> login(String body) {
        JsonNode payload = object(body);
        String loginId = text(payload.get("loginId"));
        String password = text(payload.get("password"));
        if (loginId.isBlank() || password.isBlank()) {
            throw new IllegalArgumentException("아이디와 비밀번호를 입력해 주세요.");
        }
        Account account = accounts.findByLoginId(loginId).orElse(null);
        if (account == null || !passwords.matches(password, account.passwordHash())) {
            throw new AuthenticationFailure("아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        if (passwords.needsUpgrade(account.passwordHash())) {
            Account upgraded = account.withPasswordHash(passwords.hash(password));
            accounts.replace(upgraded);
            account = upgraded;
        }
        return session(account);
    }

    public Map<String, Object> currentUser(String userId) {
        Account account = accounts.findByUserId(userId)
                .orElseThrow(() -> new AuthenticationFailure("인증된 사용자를 찾을 수 없습니다."));
        return Map.of("user", publicUser(account));
    }

    public Map<String, Object> update(String userId, String body) {
        Account account = accounts.findByUserId(userId)
                .orElseThrow(() -> new AuthenticationFailure("인증된 사용자를 찾을 수 없습니다."));
        JsonNode payload = object(body);
        String name = payload.has("name") ? name(payload.get("name")) : account.name();
        String email = payload.has("email") ? email(payload.get("email")) : account.email();
        String birth = payload.has("dateOfBirth") ? birth(payload.get("dateOfBirth")) : account.dateOfBirth();
        String region = payload.has("region") ? optionalRegion(payload.get("region")) : account.region();
        Integer income = payload.has("incomeBracket") ? optionalIncome(payload.get("incomeBracket")) : account.incomeBracket();
        Account updated = account.withDetails(name, email, birth, region, income);
        accounts.replace(updated);
        return Map.of("user", publicUser(updated));
    }

    public Map<String, Object> loginIdAvailability(String loginIdValue) {
        String loginId = loginId(loginIdValue == null ? null : MAPPER.getNodeFactory().textNode(loginIdValue));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("loginId", loginId);
        body.put("available", !accounts.loginIdExists(loginId));
        return body;
    }

    private Map<String, Object> session(Account account) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", tokens.issue(account.userId()));
        body.put("user", publicUser(account));
        body.put("localDevOnly", true);
        return body;
    }

    static Map<String, Object> publicUser(Account account) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", account.userId());
        user.put("loginId", account.loginId());
        user.put("name", account.name());
        user.put("email", account.email());
        user.put("dateOfBirth", account.dateOfBirth());
        return user;
    }

    private static JsonNode object(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("JSON 본문을 확인해 주세요.");
        }
        try {
            JsonNode payload = MAPPER.readTree(body);
            if (!payload.isObject()) {
                throw new IllegalArgumentException("JSON 본문을 확인해 주세요.");
            }
            return payload;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON 본문을 확인해 주세요.");
        }
    }

    private static String loginId(JsonNode value) {
        String loginId = text(value);
        if (!LOGIN_ID.matcher(loginId).matches()) {
            throw new IllegalArgumentException("아이디는 영문, 숫자, 밑줄, 하이픈으로 4자 이상 50자 이하여야 합니다.");
        }
        return loginId;
    }

    private static String email(JsonNode value) {
        String email = text(value);
        if (email.length() > 254 || !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("이메일 형식을 확인해 주세요.");
        }
        return email;
    }

    private static String name(JsonNode value) {
        String name = text(value);
        if (name.isBlank() || name.length() > 50) {
            throw new IllegalArgumentException("이름을 1자 이상 50자 이하로 입력해 주세요.");
        }
        return name;
    }

    private static String required(JsonNode value, String label) {
        String text = text(value);
        if (text.isBlank()) {
            throw new IllegalArgumentException(label + "를 입력해 주세요.");
        }
        return text;
    }

    private static void validatePassword(String password, String loginId) {
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("비밀번호는 72바이트 이하여야 합니다.");
        }
        if (password.length() < 8) {
            throw new IllegalArgumentException("비밀번호는 8자 이상이어야 합니다.");
        }
        if (password.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("비밀번호를 숫자로만 구성할 수 없습니다.");
        }
        if (password.equalsIgnoreCase(loginId)) {
            throw new IllegalArgumentException("비밀번호가 아이디와 너무 비슷합니다.");
        }
    }

    private static String birth(JsonNode value) {
        String text = text(value);
        LocalDate born = null;
        for (DateTimeFormatter format : new DateTimeFormatter[] {
                DateTimeFormatter.BASIC_ISO_DATE,
                DateTimeFormatter.ISO_LOCAL_DATE
        }) {
            try {
                born = LocalDate.parse(text, format);
                break;
            } catch (DateTimeParseException ex) {
                born = null;
            }
        }
        if (born == null || born.getYear() < 1900 || born.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("생년월일은 YYYYMMDD 또는 YYYY-MM-DD 형식이어야 합니다.");
        }
        return born.format(ISO);
    }

    private static String optionalRegion(JsonNode value) {
        if (value == null || value.isNull() || text(value).isBlank()) {
            return null;
        }
        String region = text(value);
        if (region.length() > 100) {
            throw new IllegalArgumentException("지역은 100자 이하여야 합니다.");
        }
        return region;
    }

    private static Integer optionalIncome(JsonNode value) {
        if (value == null || value.isNull() || text(value).isBlank()) {
            return null;
        }
        try {
            int income = Integer.parseInt(text(value));
            if (income < 1 || income > 10) {
                throw new IllegalArgumentException("소득분위는 1부터 10 사이의 숫자여야 합니다.");
            }
            return income;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("소득분위는 1부터 10 사이의 숫자여야 합니다.");
        }
    }

    private static String text(JsonNode value) {
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText("").trim();
    }

    public static final class AuthenticationFailure extends RuntimeException {
        public AuthenticationFailure(String message) {
            super(message);
        }
    }
}
