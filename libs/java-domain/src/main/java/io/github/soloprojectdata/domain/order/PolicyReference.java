package io.github.soloprojectdata.domain.order;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 외부 수익 정책의 버전과 근거 데이터 hash를 보존한다.
 */
public record PolicyReference(
        String policyId,
        String policyVersion,
        String evidenceSha256
) {

    private static final Pattern IDENTIFIER = Pattern.compile(
            "[a-zA-Z][a-zA-Z0-9._\\-]{0,63}"
    );
    private static final Pattern VERSION = Pattern.compile(
            "[a-zA-Z0-9][a-zA-Z0-9._\\-]{0,63}"
    );
    private static final Pattern SHA256 = Pattern.compile("[a-f0-9]{64}");

    public PolicyReference {
        policyId = requirePattern(policyId, IDENTIFIER, "policyId");
        policyVersion = requirePattern(policyVersion, VERSION, "policyVersion");
        evidenceSha256 = requirePattern(
                evidenceSha256,
                SHA256,
                "evidenceSha256"
        );
    }

    private static String requirePattern(
            String value,
            Pattern pattern,
            String name
    ) {
        Objects.requireNonNull(value, name);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " 형식이 올바르지 않습니다");
        }
        return value;
    }
}
