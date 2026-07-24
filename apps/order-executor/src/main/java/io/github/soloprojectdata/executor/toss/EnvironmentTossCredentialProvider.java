package io.github.soloprojectdata.executor.toss;

import java.util.Map;
import java.util.Objects;

/**
 * 저장소 파일이 아닌 프로세스 환경에서만 자격증명을 읽는다.
 *
 * <p>이 Provider는 아직 애플리케이션 기본 구성에 연결되지 않는다.</p>
 */
public final class EnvironmentTossCredentialProvider
        implements TossCredentialProvider {

    public static final String CLIENT_ID_VARIABLE = "TOSS_CLIENT_ID";
    public static final String CLIENT_SECRET_VARIABLE = "TOSS_CLIENT_SECRET";

    private final Map<String, String> environment;

    public EnvironmentTossCredentialProvider() {
        this(System.getenv());
    }

    EnvironmentTossCredentialProvider(Map<String, String> environment) {
        this.environment = Map.copyOf(
                Objects.requireNonNull(environment, "environment")
        );
    }

    @Override
    public TossCredentials load() {
        String clientId = environment.get(CLIENT_ID_VARIABLE);
        String clientSecret = environment.get(CLIENT_SECRET_VARIABLE);
        if (clientId == null || clientSecret == null) {
            throw new IllegalStateException(
                    "Toss 자격증명 환경 변수가 모두 제공되지 않았습니다"
            );
        }
        return new TossCredentials(clientId, clientSecret);
    }
}
