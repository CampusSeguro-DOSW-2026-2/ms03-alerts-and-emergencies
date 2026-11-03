package edu.eci.dosw.campusseguro.alertas.repository;

import edu.eci.dosw.campusseguro.alertas.dto.request.AlertFilter;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertType;
import jakarta.persistence.criteria.Join;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class AlertSpecifications {

    private AlertSpecifications() {
    }

    public static Specification<AlertEntity> fromFilter(
            AlertFilter filter, ZoneId zone) {

        return Specification.allOf(
                hasStatus(filter.status()),
                hasAnyType(filter.types()),
                createdFrom(startOfDay(filter.startDate(), zone)),
                createdBefore(startOfDay(nextDay(filter.endDate()), zone)),
                hasSimulationId(filter.simulationId()));
    }

    private static Specification<AlertEntity> hasStatus(
            AlertStatus status) {

        return (root, query, cb) -> status == null
                ? cb.conjunction()
                : cb.equal(root.get("status"), status);
    }

    private static Specification<AlertEntity> hasAnyType(
            List<AlertType> types) {

        return (root, query, cb) -> {
            if (types == null || types.isEmpty()) {
                return cb.conjunction();
            }

            query.distinct(true);
            Join<AlertEntity, AlertType> typeJoin = root.join("types");
            return typeJoin.in(types);
        };
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

    private static Specification<AlertEntity> hasSimulationId(
            UUID simulationId) {

        return (root, query, cb) -> simulationId == null
                ? cb.conjunction()
                : cb.equal(root.get("simulationId"), simulationId);
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
