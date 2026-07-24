package io.github.soloprojectdata.executor.toss;

/**
 * 외부 Broker 네트워크 접근의 최소 Gate Snapshot이다.
 */
public record TossNetworkAuthorization(
        boolean productionEnvironment,
        boolean externalNetworkEnabled,
        boolean credentialAvailable,
        boolean artifactApproved
) {

    public void requireAllowed() {
        if (!productionEnvironment
                || !externalNetworkEnabled
                || !credentialAvailable
                || !artifactApproved) {
            throw new IllegalStateException(
                    "Toss 외부 네트워크 Gate가 모두 충족되지 않았습니다"
            );
        }
    }
}
