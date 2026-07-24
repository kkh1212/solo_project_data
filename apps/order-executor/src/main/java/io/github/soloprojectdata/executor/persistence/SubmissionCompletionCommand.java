package io.github.soloprojectdata.executor.persistence;

import io.github.soloprojectdata.executor.toss.TossOrderSubmissionOutcome;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record SubmissionCompletionCommand(
        UUID attemptId,
        TossOrderSubmissionOutcome outcome,
        Optional<UUID> reconciliationCaseId,
        Instant completedAt
) {

    public SubmissionCompletionCommand {
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(outcome, "outcome");
        reconciliationCaseId = Objects.requireNonNull(
                reconciliationCaseId,
                "reconciliationCaseId"
        ).map(Objects::requireNonNull);
        Objects.requireNonNull(completedAt, "completedAt");
        boolean unknown = outcome.status()
                == TossOrderSubmissionOutcome.Status
                .UNKNOWN_REQUIRES_RECONCILIATION;
        if (unknown != reconciliationCaseId.isPresent()) {
            throw new IllegalArgumentException(
                    "UNKNOWN 결과에만 Reconciliation Case ID가 필요합니다"
            );
        }
    }
}
