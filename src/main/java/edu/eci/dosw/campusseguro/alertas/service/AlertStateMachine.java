package edu.eci.dosw.campusseguro.alertas.service;

import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidTransitionException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AlertStateMachine {

    // Mapa de transiciones permitidas: estado actual -> estados a los que puede pasar
    private static final Map<AlertStatus, Set<AlertStatus>> ALLOWED = Map.of(
            AlertStatus.DRAFT, EnumSet.of(AlertStatus.SCHEDULED, AlertStatus.ACTIVE),
            AlertStatus.SCHEDULED, EnumSet.of(AlertStatus.ACTIVE),
            AlertStatus.ACTIVE, EnumSet.of(AlertStatus.ENDED),
            AlertStatus.ENDED, EnumSet.of(AlertStatus.ARCHIVED),
            AlertStatus.ARCHIVED, EnumSet.noneOf(AlertStatus.class));

    public boolean canTransition(AlertStatus from, AlertStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public void assertTransition(AlertStatus from, AlertStatus to) {
        if (!canTransition(from, to)) {
            throw new InvalidTransitionException(from, to);
        }
    }
}