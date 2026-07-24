package io.github.soloprojectdata.executor;

/**
 * Broker Adapter의 안전 경계를 나타내는 최소 인터페이스다.
 *
 * <p>주문 제출 계약과 외부 Endpoint는 후속 단계의 공식 명세 확인 전 추가하지 않는다.</p>
 */
public interface BrokerGateway {

    BrokerGatewayType type();

    boolean externalNetworkEnabled();
}
