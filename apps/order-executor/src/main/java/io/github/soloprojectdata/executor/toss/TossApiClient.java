package io.github.soloprojectdata.executor.toss;

import io.github.soloprojectdata.domain.Instrument;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * JDK HttpClient 기반 Toss Securities Open API Adapter다.
 *
 * <p>자동 재시도를 하지 않는다. 주문·취소 응답이 모호하면
 * {@code UNKNOWN_REQUIRES_RECONCILIATION}을 반환한다.</p>
 */
public final class TossApiClient {

    private static final Pattern BROKER_ORDER_ID = Pattern.compile(
            "[a-zA-Z0-9\\-_]+"
    );
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final JsonMapper jsonMapper;
    private final URI baseUri;
    private final Duration requestTimeout;
    private final Clock clock;

    public TossApiClient(
            HttpClient httpClient,
            JsonMapper jsonMapper,
            URI baseUri,
            Duration requestTimeout,
            Clock clock
    ) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
        this.baseUri = requireAllowedBaseUri(baseUri);
        this.requestTimeout = Objects.requireNonNullElse(
                requestTimeout,
                DEFAULT_TIMEOUT
        );
        this.clock = Objects.requireNonNull(clock, "clock");
        if (this.requestTimeout.isZero() || this.requestTimeout.isNegative()) {
            throw new IllegalArgumentException("requestTimeout은 0보다 커야 합니다");
        }
    }

    public TossAccessToken issueAccessToken(
            TossCredentials credentials,
            TossNetworkAuthorization authorization
    ) {
        Objects.requireNonNull(credentials, "credentials");
        Objects.requireNonNull(authorization, "authorization").requireAllowed();
        String form = "grant_type=client_credentials"
                + "&client_id=" + formEncode(credentials.clientId())
                + "&client_secret=" + formEncode(credentials.clientSecret());
        HttpRequest request = HttpRequest.newBuilder(resolve(TossApiContract.TOKEN_PATH))
                .timeout(requestTimeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = sendForRead(request);
        if (response.statusCode() != 200) {
            TossError error = parseError(response.body());
            throw new TossApiException(
                    "Toss OAuth Token 발급에 실패했습니다",
                    response.statusCode(),
                    error.code()
            );
        }
        JsonNode root = parseJson(response.body());
        String tokenType = requiredText(root, "token_type");
        if (!"Bearer".equals(tokenType)) {
            throw new TossApiException(
                    "지원하지 않는 Toss Token 유형입니다",
                    200,
                    "unsupported-token-type"
            );
        }
        long expiresIn = requiredLong(root, "expires_in");
        if (expiresIn <= 0) {
            throw new TossApiException(
                    "Toss Token 만료 시간이 올바르지 않습니다",
                    200,
                    "invalid-token-expiry"
            );
        }
        return new TossAccessToken(
                requiredText(root, "access_token"),
                clock.instant().plusSeconds(expiresIn)
        );
    }

    public List<TossAccount> listAccounts(
            TossAccessToken token,
            TossNetworkAuthorization authorization
    ) {
        Objects.requireNonNull(authorization, "authorization").requireAllowed();
        HttpRequest request = authorizedBuilder(
                TossApiContract.ACCOUNTS_PATH,
                requireUsableToken(token)
        ).GET().build();
        HttpResponse<String> response = sendForRead(request);
        requireSuccess(response, "Toss 계좌 목록 조회");
        JsonNode result = requiredNode(parseJson(response.body()), "result");
        if (!result.isArray()) {
            throw contractError("Toss 계좌 목록 result가 배열이 아닙니다");
        }
        List<TossAccount> accounts = new ArrayList<>();
        for (JsonNode account : result) {
            accounts.add(new TossAccount(
                    requiredLong(account, "accountSeq"),
                    requiredText(account, "accountType")
            ));
        }
        return List.copyOf(accounts);
    }

    public TossOrderSubmissionOutcome submitOrder(
            TossAccessToken token,
            long accountSequence,
            TossOrderRequest order,
            TossOrderAuthorization authorization
    ) {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(authorization, "authorization").requireAllowed(
                accountSequence,
                order.clientOrderId(),
                clock.instant()
        );
        TossAccessToken usableToken = requireUsableToken(token);
        final String body;
        try {
            body = jsonMapper.writeValueAsString(order.toPayload());
        } catch (JacksonException exception) {
            throw new IllegalStateException("주문 요청 JSON 생성에 실패했습니다", exception);
        }
        HttpRequest request = accountBuilder(
                TossApiContract.ORDERS_PATH,
                usableToken,
                accountSequence
        )
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
            return classifySubmissionResponse(response, order.clientOrderId());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return TossOrderSubmissionOutcome.unknown(
                    order.clientOrderId(),
                    "transport-interrupted",
                    0
            );
        } catch (IOException exception) {
            return TossOrderSubmissionOutcome.unknown(
                    order.clientOrderId(),
                    "transport-error",
                    0
            );
        }
    }

    public TossOrderSnapshot getOrder(
            TossAccessToken token,
            long accountSequence,
            String brokerOrderId,
            TossAccountAuthorization authorization
    ) {
        Objects.requireNonNull(authorization, "authorization").requireAllowed(
                accountSequence
        );
        String path = TossApiContract.ORDERS_PATH + "/" + orderIdPath(brokerOrderId);
        HttpRequest request = accountBuilder(
                path,
                requireUsableToken(token),
                accountSequence
        ).GET().build();
        HttpResponse<String> response = sendForRead(request);
        requireSuccess(response, "Toss 주문 상세 조회");
        JsonNode order = requiredNode(parseJson(response.body()), "result");
        String currency = requiredText(order, "currency");
        if (!"USD".equals(currency)) {
            throw contractError("미국주식 주문 조회의 통화가 USD가 아닙니다");
        }
        return new TossOrderSnapshot(
                requiredText(order, "orderId"),
                Instrument.usEquity(requiredText(order, "symbol")),
                requiredText(order, "side"),
                requiredText(order, "orderType"),
                requiredText(order, "timeInForce"),
                requiredText(order, "status"),
                decimal(order, "quantity"),
                nullableDecimal(order, "price"),
                OffsetDateTime.parse(requiredText(order, "orderedAt"))
        );
    }

    public TossOrderOperationOutcome cancelOrder(
            TossAccessToken token,
            long accountSequence,
            String brokerOrderId,
            TossAccountAuthorization authorization
    ) {
        Objects.requireNonNull(authorization, "authorization").requireAllowed(
                accountSequence
        );
        String path = TossApiContract.ORDERS_PATH
                + "/"
                + orderIdPath(brokerOrderId)
                + "/cancel";
        HttpRequest request = accountBuilder(
                path,
                requireUsableToken(token),
                accountSequence
        )
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
            return classifyOperationResponse(response);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return TossOrderOperationOutcome.unknown("transport-interrupted", 0);
        } catch (IOException exception) {
            return TossOrderOperationOutcome.unknown("transport-error", 0);
        }
    }

    private TossOrderSubmissionOutcome classifySubmissionResponse(
            HttpResponse<String> response,
            String clientOrderId
    ) {
        if (response.statusCode() == 200) {
            try {
                JsonNode result = requiredNode(parseJson(response.body()), "result");
                String returnedClientOrderId = nullableText(result, "clientOrderId");
                if (!clientOrderId.equals(returnedClientOrderId)) {
                    return TossOrderSubmissionOutcome.unknown(
                            clientOrderId,
                            "client-order-id-mismatch",
                            200
                    );
                }
                return TossOrderSubmissionOutcome.accepted(
                        requiredText(result, "orderId"),
                        clientOrderId
                );
            } catch (RuntimeException exception) {
                return TossOrderSubmissionOutcome.unknown(
                        clientOrderId,
                        "invalid-success-response",
                        200
                );
            }
        }
        TossError error = parseError(response.body());
        if (isConclusiveRejection(response.statusCode())) {
            return TossOrderSubmissionOutcome.rejected(
                    clientOrderId,
                    error.code(),
                    response.statusCode()
            );
        }
        return TossOrderSubmissionOutcome.unknown(
                clientOrderId,
                error.code(),
                response.statusCode()
        );
    }

    private TossOrderOperationOutcome classifyOperationResponse(
            HttpResponse<String> response
    ) {
        if (response.statusCode() == 200) {
            try {
                JsonNode result = requiredNode(parseJson(response.body()), "result");
                return TossOrderOperationOutcome.accepted(
                        requiredText(result, "orderId")
                );
            } catch (RuntimeException exception) {
                return TossOrderOperationOutcome.unknown(
                        "invalid-success-response",
                        200
                );
            }
        }
        TossError error = parseError(response.body());
        if (response.statusCode() == 400
                || response.statusCode() == 401
                || response.statusCode() == 403
                || response.statusCode() == 404
                || response.statusCode() == 422) {
            return TossOrderOperationOutcome.rejected(
                    error.code(),
                    response.statusCode()
            );
        }
        return TossOrderOperationOutcome.unknown(
                error.code(),
                response.statusCode()
        );
    }

    private HttpRequest.Builder authorizedBuilder(
            String path,
            TossAccessToken token
    ) {
        return HttpRequest.newBuilder(resolve(path))
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("Authorization", token.authorizationValue());
    }

    private HttpRequest.Builder accountBuilder(
            String path,
            TossAccessToken token,
            long accountSequence
    ) {
        if (accountSequence <= 0) {
            throw new IllegalArgumentException("accountSequence는 0보다 커야 합니다");
        }
        return authorizedBuilder(path, token)
                .header(
                        TossApiContract.ACCOUNT_HEADER,
                        Long.toString(accountSequence)
                );
    }

    private TossAccessToken requireUsableToken(TossAccessToken token) {
        Objects.requireNonNull(token, "token");
        if (!token.isUsableAt(clock.instant())) {
            throw new IllegalStateException("Toss Access Token이 만료되었습니다");
        }
        return token;
    }

    private HttpResponse<String> sendForRead(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TossApiException("Toss API 호출이 중단되었습니다", exception);
        } catch (IOException exception) {
            throw new TossApiException("Toss API 전송에 실패했습니다", exception);
        }
    }

    private void requireSuccess(HttpResponse<String> response, String operation) {
        if (response.statusCode() != 200) {
            TossError error = parseError(response.body());
            throw new TossApiException(
                    operation + "에 실패했습니다",
                    response.statusCode(),
                    error.code()
            );
        }
    }

    private JsonNode parseJson(String body) {
        try {
            JsonNode node = jsonMapper.readTree(body);
            if (node == null) {
                throw contractError("Toss JSON 응답이 비어 있습니다");
            }
            return node;
        } catch (JacksonException exception) {
            throw new TossApiException(
                    "Toss JSON 응답을 해석할 수 없습니다",
                    0,
                    "invalid-json"
            );
        }
    }

    private TossError parseError(String body) {
        try {
            JsonNode error = requiredNode(parseJson(body), "error");
            return new TossError(
                    nullableText(error, "code"),
                    nullableText(error, "requestId")
            );
        } catch (RuntimeException exception) {
            return new TossError("unparseable-error", null);
        }
    }

    private static JsonNode requiredNode(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            throw contractError("Toss 응답 필드가 없습니다: " + field);
        }
        return node;
    }

    private static String requiredText(JsonNode parent, String field) {
        String value = nullableText(parent, field);
        if (value == null || value.isBlank()) {
            throw contractError("Toss 문자열 필드가 없습니다: " + field);
        }
        return value;
    }

    private static String nullableText(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        return node == null || node.isNull() ? null : node.stringValue();
    }

    private static long requiredLong(JsonNode parent, String field) {
        JsonNode node = requiredNode(parent, field);
        if (!node.isIntegralNumber()) {
            throw contractError("Toss 정수 필드 형식이 올바르지 않습니다: " + field);
        }
        return node.longValue();
    }

    private static java.math.BigDecimal decimal(JsonNode parent, String field) {
        return new java.math.BigDecimal(requiredText(parent, field));
    }

    private static java.math.BigDecimal nullableDecimal(
            JsonNode parent,
            String field
    ) {
        String value = nullableText(parent, field);
        return value == null ? null : new java.math.BigDecimal(value);
    }

    private static boolean isConclusiveRejection(int statusCode) {
        return statusCode == 400
                || statusCode == 401
                || statusCode == 403
                || statusCode == 404
                || statusCode == 422;
    }

    private URI resolve(String path) {
        return baseUri.resolve(path);
    }

    private static URI requireAllowedBaseUri(URI value) {
        Objects.requireNonNull(value, "baseUri");
        if (TossApiContract.OFFICIAL_BASE_URI.equals(value)) {
            return value;
        }
        String host = value.getHost();
        boolean loopback = "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host);
        if (loopback && "http".equalsIgnoreCase(value.getScheme())) {
            return value;
        }
        throw new IllegalArgumentException(
                "Toss API Base URI는 공식 주소 또는 테스트 Loopback만 허용합니다"
        );
    }

    private static String formEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String orderIdPath(String orderId) {
        Objects.requireNonNull(orderId, "brokerOrderId");
        if (!BROKER_ORDER_ID.matcher(orderId).matches()) {
            throw new IllegalArgumentException("Broker Order ID 형식이 올바르지 않습니다");
        }
        return URLEncoder.encode(orderId, StandardCharsets.UTF_8);
    }

    private static TossApiException contractError(String message) {
        return new TossApiException(message, 0, "contract-violation");
    }

    private record TossError(String code, String requestId) {

        private TossError {
            code = code == null || code.isBlank() ? "unknown-error" : code;
        }
    }
}
