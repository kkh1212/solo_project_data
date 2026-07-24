package io.github.soloprojectdata.domain.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StateMachineContractTest {

    @Test
    void 언어중립계약과Java전이가일치한다() throws IOException {
        Set<String> expected = readContractTransitions();
        Set<String> actual = new HashSet<>();

        collectCandidateTransitions(actual);
        collectIntentTransitions(actual);
        collectReservationTransitions(actual);
        collectBrokerOrderTransitions(actual);

        assertEquals(expected, actual);
    }

    private static Set<String> readContractTransitions() throws IOException {
        Path current = Path.of("").toAbsolutePath();
        Path contract = null;
        while (current != null) {
            Path candidate = current.resolve(
                    "contracts/domain/state-transitions.csv"
            );
            if (Files.exists(candidate)) {
                contract = candidate;
                break;
            }
            current = current.getParent();
        }
        if (contract == null) {
            throw new IllegalStateException("상태 전이 계약 파일을 찾을 수 없습니다");
        }

        Set<String> transitions = new HashSet<>();
        List<String> lines = Files.readAllLines(contract);
        for (String line : lines.subList(1, lines.size())) {
            String[] columns = line.split(",", -1);
            transitions.add(key(columns[0], columns[1], columns[2]));
        }
        return transitions;
    }

    private static void collectCandidateTransitions(Set<String> transitions) {
        for (OrderCandidateStatus from : OrderCandidateStatus.values()) {
            for (OrderCandidateStatus to : OrderCandidateStatus.values()) {
                if (from.canTransitionTo(to)) {
                    transitions.add(key("orderCandidate", from, to));
                }
            }
        }
    }

    private static void collectIntentTransitions(Set<String> transitions) {
        for (OrderIntentStatus from : OrderIntentStatus.values()) {
            for (OrderIntentStatus to : OrderIntentStatus.values()) {
                if (from.canTransitionTo(to)) {
                    transitions.add(key("orderIntent", from, to));
                }
            }
        }
    }

    private static void collectReservationTransitions(Set<String> transitions) {
        for (RiskReservationStatus from : RiskReservationStatus.values()) {
            for (RiskReservationStatus to : RiskReservationStatus.values()) {
                try {
                    from.transitionTo(
                            to,
                            BrokerSubmissionCertainty.NOT_SUBMITTED
                    );
                    transitions.add(key("riskReservation", from, to));
                } catch (InvalidStateTransitionException ignored) {
                    // 허용 전이만 수집한다.
                }
            }
        }
    }

    private static void collectBrokerOrderTransitions(Set<String> transitions) {
        for (BrokerOrderStatus from : BrokerOrderStatus.values()) {
            for (BrokerOrderStatus to : BrokerOrderStatus.values()) {
                try {
                    from.transitionTo(to, BrokerStateEvidence.RECONCILIATION);
                    transitions.add(key("brokerOrder", from, to));
                } catch (InvalidStateTransitionException ignored) {
                    // 허용 전이만 수집한다.
                }
            }
        }
    }

    private static String key(String machine, Enum<?> from, Enum<?> to) {
        return key(machine, from.name(), to.name());
    }

    private static String key(String machine, String from, String to) {
        return machine + "," + from + "," + to;
    }
}
