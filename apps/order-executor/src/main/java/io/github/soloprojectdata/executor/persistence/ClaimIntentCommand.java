package io.github.soloprojectdata.executor.persistence;

import io.github.soloprojectdata.domain.id.OrderIntentId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record ClaimIntentCommand(
        UUID eventId,
        OrderIntentId orderIntentId,
        UUID brokerOrderRecordId,
        String accountAlias,
        String clientOrderId,
        String payloadSha256,
        Instant receivedAt
) {

    private static final Pattern ACCOUNT_ALIAS = Pattern.compile(
            "[a-z][a-z0-9_\\-]{2,31}"
    );
    private static final Pattern CLIENT_ORDER_ID = Pattern.compile(
            "[A-Za-z0-9_\\-]{1,36}"
    );
    private static final Pattern SHA256 = Pattern.compile("[a-f0-9]{64}");

    public ClaimIntentCommand {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(orderIntentId, "orderIntentId");
        Objects.requireNonNull(brokerOrderRecordId, "brokerOrderRecordId");
        accountAlias = require(accountAlias, ACCOUNT_ALIAS, "accountAlias");
        clientOrderId = require(
                clientOrderId,
                CLIENT_ORDER_ID,
                "clientOrderId"
        );
        payloadSha256 = require(payloadSha256, SHA256, "payloadSha256");
        Objects.requireNonNull(receivedAt, "receivedAt");
    }

    private static String require(String value, Pattern pattern, String name) {
        Objects.requireNonNull(value, name);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " 형식이 올바르지 않습니다");
        }
        return value;
    }
}
