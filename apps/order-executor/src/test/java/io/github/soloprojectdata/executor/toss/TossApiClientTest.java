package io.github.soloprojectdata.executor.toss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.soloprojectdata.domain.Instrument;
import io.github.soloprojectdata.domain.Price;
import io.github.soloprojectdata.domain.Quantity;
import io.github.soloprojectdata.domain.state.BrokerOrderStatus;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TossApiClientTest {

    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");

    private HttpServer server;
    private URI baseUri;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUri = URI.create(
                "http://127.0.0.1:" + server.getAddress().getPort()
        );
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void oauthClientCredentials로Token을발급하되값을출력하지않는다() {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext(TossApiContract.TOKEN_PATH, exchange -> {
            requestBody.set(readBody(exchange));
            respond(
                    exchange,
                    200,
                    """
                    {
                      "access_token": "synthetic-token",
                      "token_type": "Bearer",
                      "expires_in": 3600
                    }
                    """
            );
        });
        TossCredentials credentials = new TossCredentials(
                "synthetic-client",
                "synthetic-secret"
        );

        TossAccessToken token = client(Duration.ofSeconds(1)).issueAccessToken(
                credentials,
                networkAuthorization()
        );

        assertEquals(NOW.plusSeconds(3600), token.expiresAt());
        assertTrue(requestBody.get().contains("grant_type=client_credentials"));
        assertTrue(requestBody.get().contains("client_id=synthetic-client"));
        assertFalse(credentials.toString().contains("synthetic-client"));
        assertFalse(token.toString().contains("synthetic-token"));
    }

    @Test
    void 계좌목록은계좌번호를버리고Sequence와Type만반환한다() {
        server.createContext(TossApiContract.ACCOUNTS_PATH, exchange -> respond(
                exchange,
                200,
                """
                {
                  "result": [{
                    "accountNo": "synthetic-account",
                    "accountSeq": 1,
                    "accountType": "BROKERAGE"
                  }]
                }
                """
        ));

        var accounts = client(Duration.ofSeconds(1)).listAccounts(
                token(),
                networkAuthorization()
        );

        assertEquals(1, accounts.size());
        assertEquals(new TossAccount(1, "BROKERAGE"), accounts.getFirst());
    }

    @Test
    void 승인된주문은계좌Header와멱등성Key를포함해한번만전송한다() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> accountHeader = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext(TossApiContract.ORDERS_PATH, exchange -> {
            calls.incrementAndGet();
            accountHeader.set(
                    exchange.getRequestHeaders().getFirst(
                            TossApiContract.ACCOUNT_HEADER
                    )
            );
            requestBody.set(readBody(exchange));
            respond(
                    exchange,
                    200,
                    """
                    {
                      "result": {
                        "orderId": "broker-order-1",
                        "clientOrderId": "intent-1"
                      }
                    }
                    """
            );
        });
        TossOrderRequest order = limitOrder("intent-1");

        TossOrderSubmissionOutcome outcome = client(
                Duration.ofSeconds(1)
        ).submitOrder(token(), 1, order, orderAuthorization("intent-1"));

        assertEquals(
                TossOrderSubmissionOutcome.Status.ACCEPTED,
                outcome.status()
        );
        assertEquals("broker-order-1", outcome.brokerOrderId());
        assertEquals("1", accountHeader.get());
        assertTrue(requestBody.get().contains("\"clientOrderId\":\"intent-1\""));
        assertEquals(1, calls.get());
    }

    @Test
    void 명시적거절은미제출로분류하고자동재시도하지않는다() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext(TossApiContract.ORDERS_PATH, exchange -> {
            calls.incrementAndGet();
            respond(
                    exchange,
                    422,
                    error("insufficient-buying-power")
            );
        });

        TossOrderSubmissionOutcome outcome = client(
                Duration.ofSeconds(1)
        ).submitOrder(
                token(),
                1,
                limitOrder("intent-1"),
                orderAuthorization("intent-1")
        );

        assertEquals(
                TossOrderSubmissionOutcome.Status.REJECTED_NOT_SUBMITTED,
                outcome.status()
        );
        assertEquals("insufficient-buying-power", outcome.errorCode());
        assertEquals(1, calls.get());
    }

    @Test
    void 모호한Http응답은Unknown으로분류하고자동재시도하지않는다() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext(TossApiContract.ORDERS_PATH, exchange -> {
            calls.incrementAndGet();
            respond(exchange, 409, error("request-in-progress"));
        });

        TossOrderSubmissionOutcome outcome = client(
                Duration.ofSeconds(1)
        ).submitOrder(
                token(),
                1,
                limitOrder("intent-1"),
                orderAuthorization("intent-1")
        );

        assertEquals(
                TossOrderSubmissionOutcome.Status.UNKNOWN_REQUIRES_RECONCILIATION,
                outcome.status()
        );
        assertEquals(1, calls.get());
    }

    @Test
    void timeout은Unknown으로분류하고자동재시도하지않는다() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext(TossApiContract.ORDERS_PATH, exchange -> {
            calls.incrementAndGet();
            try {
                Thread.sleep(150);
                respond(
                        exchange,
                        200,
                        """
                        {"result":{"orderId":"late","clientOrderId":"intent-1"}}
                        """
                );
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                exchange.close();
            }
        });

        TossOrderSubmissionOutcome outcome = client(
                Duration.ofMillis(20)
        ).submitOrder(
                token(),
                1,
                limitOrder("intent-1"),
                orderAuthorization("intent-1")
        );

        assertEquals(
                TossOrderSubmissionOutcome.Status.UNKNOWN_REQUIRES_RECONCILIATION,
                outcome.status()
        );
        assertEquals(1, calls.get());
    }

    @Test
    void 주문조회는알수없는Broker상태를Unknown으로보존한다() {
        server.createContext(
                TossApiContract.ORDERS_PATH + "/broker-order-1",
                exchange -> respond(
                        exchange,
                        200,
                        """
                        {
                          "result": {
                            "orderId": "broker-order-1",
                            "symbol": "AAPL",
                            "side": "BUY",
                            "orderType": "FUTURE_CODE",
                            "timeInForce": "FUTURE_TIF",
                            "status": "FUTURE_STATUS",
                            "price": "185.50",
                            "quantity": "1",
                            "currency": "USD",
                            "orderedAt": "2026-07-24T09:30:00-04:00",
                            "execution": {"filledQuantity": "0"}
                          }
                        }
                        """
                )
        );

        TossOrderSnapshot snapshot = client(Duration.ofSeconds(1)).getOrder(
                token(),
                1,
                "broker-order-1",
                TossAuthorizationTest.accountAuthorization()
        );

        assertEquals("FUTURE_CODE", snapshot.rawOrderType());
        assertEquals(BrokerOrderStatus.UNKNOWN, snapshot.normalizedStatus());
    }

    @Test
    void killSwitch중에도직접취소경로는사용할수있다() {
        server.createContext(
                TossApiContract.ORDERS_PATH + "/broker-order-1/cancel",
                exchange -> respond(
                        exchange,
                        200,
                        """
                        {"result":{"orderId":"cancel-order-1"}}
                        """
                )
        );

        TossOrderOperationOutcome outcome = client(
                Duration.ofSeconds(1)
        ).cancelOrder(
                token(),
                1,
                "broker-order-1",
                TossAuthorizationTest.accountAuthorization()
        );

        assertEquals(TossOrderOperationOutcome.Status.ACCEPTED, outcome.status());
        assertEquals("cancel-order-1", outcome.resultingOrderId());
    }

    @Test
    void 승인Gate실패는Http전송전에차단한다() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext(TossApiContract.ORDERS_PATH, exchange -> {
            calls.incrementAndGet();
            respond(exchange, 500, error("unexpected"));
        });
        TossOrderAuthorization denied = new TossOrderAuthorization(
                TossAuthorizationTest.accountAuthorization(),
                "intent-1",
                "approval-1",
                NOW.plusSeconds(60),
                false,
                true,
                true,
                true,
                true
        );

        assertThrows(
                IllegalStateException.class,
                () -> client(Duration.ofSeconds(1)).submitOrder(
                        token(),
                        1,
                        limitOrder("intent-1"),
                        denied
                )
        );
        assertEquals(0, calls.get());
    }

    @Test
    void 공식주소와Loopback외BaseUri는거절한다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TossApiClient(
                        HttpClient.newHttpClient(),
                        JsonMapper.builder().build(),
                        URI.create("https://example.com"),
                        Duration.ofSeconds(1),
                        fixedClock()
                )
        );
    }

    private TossApiClient client(Duration timeout) {
        return new TossApiClient(
                HttpClient.newBuilder().connectTimeout(timeout).build(),
                JsonMapper.builder().build(),
                baseUri,
                timeout,
                fixedClock()
        );
    }

    private static TossAccessToken token() {
        return new TossAccessToken("synthetic-token", NOW.plusSeconds(3600));
    }

    private static TossNetworkAuthorization networkAuthorization() {
        return new TossNetworkAuthorization(true, true, true, true);
    }

    private static TossOrderAuthorization orderAuthorization(String clientOrderId) {
        return new TossOrderAuthorization(
                TossAuthorizationTest.accountAuthorization(),
                clientOrderId,
                "approval-1",
                NOW.plusSeconds(60),
                true,
                true,
                true,
                true,
                true
        );
    }

    private static TossOrderRequest limitOrder(String clientOrderId) {
        return TossOrderRequest.quantityBased(
                clientOrderId,
                Instrument.usEquity("AAPL"),
                TossOrderSide.BUY,
                TossOrderType.LIMIT,
                TossTimeInForce.DAY,
                Quantity.exact("1"),
                Price.exact("185.50", "USD")
        );
    }

    private static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private static String error(String code) {
        return "{\"error\":{\"requestId\":\"synthetic-request\",\"code\":\""
                + code
                + "\",\"message\":\"synthetic\"}}";
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(
                exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8
        );
    }

    private static void respond(
            HttpExchange exchange,
            int status,
            String body
    ) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json"
        );
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
