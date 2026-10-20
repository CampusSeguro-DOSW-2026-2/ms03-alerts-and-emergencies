
package edu.eci.dosw.campusseguro.alertas.service;

import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AlertStateMachine {

    private final Map<AlertStatus, Set<AlertStatus>> transitions =
            new EnumMap<>(AlertStatus.class);

    public AlertStateMachine() {
        transitions.put(
                AlertStatus.DRAFT,
                EnumSet.of(
                        AlertStatus.SCHEDULED,
                        AlertStatus.ACTIVE));

        transitions.put(
                AlertStatus.SCHEDULED,
                EnumSet.of(AlertStatus.ACTIVE));

        transitions.put(
                AlertStatus.ACTIVE,
                EnumSet.of(AlertStatus.ENDED));

        transitions.put(
                AlertStatus.ENDED,
                EnumSet.of(AlertStatus.ARCHIVED));

        transitions.put(
                AlertStatus.ARCHIVED,
                EnumSet.noneOf(AlertStatus.class));
    }

    public boolean canTransition(
            AlertStatus currentStatus,
            AlertStatus nextStatus) {

        if (currentStatus == null || nextStatus == null) {
            return false;
        }

        return transitions
                .getOrDefault(
                        currentStatus,
                        EnumSet.noneOf(AlertStatus.class))
                .contains(nextStatus);
    }

    public void assertTransition(
            AlertStatus currentStatus,
            AlertStatus nextStatus) {

        if (!canTransition(currentStatus, nextStatus)) {
            throw new IllegalStateException(
                    "Invalid alert status transition from "
                            + currentStatus
                            + " to "
                            + nextStatus);
        }
    }
}
