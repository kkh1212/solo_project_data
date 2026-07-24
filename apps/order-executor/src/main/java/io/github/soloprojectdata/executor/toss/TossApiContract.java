package io.github.soloprojectdata.executor.toss;

import java.net.URI;

/**
 * 2026-07-24에 재확인한 Toss Securities Open API OAS 1.2.4 기준선이다.
 */
public final class TossApiContract {

    public static final String OAS_VERSION = "1.2.4";
    public static final URI OFFICIAL_BASE_URI = URI.create(
            "https://openapi.tossinvest.com"
    );
    public static final String ACCOUNT_HEADER = "X-Tossinvest-Account";

    static final String TOKEN_PATH = "/oauth2/token";
    static final String ACCOUNTS_PATH = "/api/v1/accounts";
    static final String ORDERS_PATH = "/api/v1/orders";

    private TossApiContract() {
    }
}
