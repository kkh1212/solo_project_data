package io.github.soloprojectdata.executor;

/**
 * Broker Adapter의 네트워크 능력을 시작 Gate에 노출하는 최소 인터페이스다.
 */
public interface BrokerGateway {

    BrokerGatewayType type();

    boolean externalNetworkEnabled();
}
