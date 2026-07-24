package io.github.soloprojectdata.executor.toss;

/**
 * Order Executor 외부로 자격증명이 전파되지 않도록 제한하는 입력 Port다.
 */
@FunctionalInterface
public interface TossCredentialProvider {

    TossCredentials load();
}
