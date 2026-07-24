package io.github.soloprojectdata.executor;

import io.github.soloprojectdata.executor.toss.TossApiClient;
import java.util.Objects;

/**
 * 외부 네트워크 능력을 명시하는 Toss Broker Adapter 경계다.
 *
 * <p>현재 애플리케이션 Bean에는 등록하지 않으며 Mock-only 시작 Gate가 이 구현을
 * 선택하면 프로세스 시작을 차단한다.</p>
 */
public final class TossBrokerGateway implements BrokerGateway {

    private final TossApiClient apiClient;

    public TossBrokerGateway(TossApiClient apiClient) {
        this.apiClient = Objects.requireNonNull(apiClient, "apiClient");
    }

    public TossApiClient apiClient() {
        return apiClient;
    }

    @Override
    public BrokerGatewayType type() {
        return BrokerGatewayType.TOSS;
    }

    @Override
    public boolean externalNetworkEnabled() {
        return true;
    }
}
