package io.github.soloprojectdata.executor;

/**
 * 외부 네트워크와 자격증명을 사용하지 않는 기본 Gateway다.
 */
public final class MockBrokerGateway implements BrokerGateway {

    @Override
    public BrokerGatewayType type() {
        return BrokerGatewayType.MOCK;
    }

    @Override
    public boolean externalNetworkEnabled() {
        return false;
    }
}
