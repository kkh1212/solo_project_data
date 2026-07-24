package io.github.soloprojectdata.executor.toss;

import java.util.Objects;

/**
 * 계좌번호를 내부 모델·로그로 전파하지 않는 Toss 계좌 선택 정보다.
 */
public record TossAccount(long accountSequence, String accountType) {

    public TossAccount {
        if (accountSequence <= 0) {
            throw new IllegalArgumentException("accountSequence는 0보다 커야 합니다");
        }
        Objects.requireNonNull(accountType, "accountType");
    }
}
