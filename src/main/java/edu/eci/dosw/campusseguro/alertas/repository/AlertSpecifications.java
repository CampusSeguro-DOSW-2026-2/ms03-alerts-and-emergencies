
package edu.eci.dosw.campusseguro.alertas.repository;

import edu.eci.dosw.campusseguro.alertas.dto.request.AlertFilter;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class AlertSpecifications {

    private AlertSpecifications() {
    }

    public static Specification<AlertEntity> fromFilter(
            AlertFilter filter, ZoneId zone) {

        return Specification.allOf(
                hasStatus(filter.status()),
                hasType(filter.type()),
                createdFrom(startOfDay(filter.startDate(), zone)),
                createdBefore(startOfDay(nextDay(filter.endDate()), zone)),
                hasEventId(filter.eventId()));
    }

    private static Specification<AlertEntity> hasStatus(
            AlertStatus status) {

        return (root, query, cb) -> status == null
                ? cb.conjunction()
                : cb.equal(root.get("status"), status);
    }

    private static Specification<AlertEntity> hasType(
            AlertType type) {

        return (root, query, cb) -> type == null
                ? cb.conjunction()
                : cb.equal(root.get("type"), type);
    }

    private static Specification<AlertEntity> createdFrom(
            Instant from) {

        return (root, query, cb) -> from == null
                ? cb.conjunction()
                : cb.greaterThanOrEqualTo(
                        root.<Instant>get("createdAt"), from);
    }

    private static Specification<AlertEntity> createdBefore(
            Instant before) {

        return (root, query, cb) -> before == null
                ? cb.conjunction()
                : cb.lessThan(
                        root.<Instant>get("createdAt"), before);
    }

    private static Specification<AlertEntity> hasEventId(
            UUID eventId) {

        return (root, query, cb) -> eventId == null
                ? cb.conjunction()
                : cb.equal(root.get("eventId"), eventId);
    }

    private static Instant startOfDay(LocalDate date, ZoneId zone) {
        return date == null
                ? null
                : date.atStartOfDay(zone).toInstant();
    }

    private static LocalDate nextDay(LocalDate date) {
        return date == null ? null : date.plusDays(1);
    }
}
