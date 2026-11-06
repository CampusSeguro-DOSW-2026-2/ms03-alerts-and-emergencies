package edu.eci.dosw.campusseguro.alertas.repository;

import edu.eci.dosw.campusseguro.alertas.dto.request.AlertFilter;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.AlertScopeItemEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertOrigin;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertType;
import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
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
                hasOrigin(filter.origin()),
                hasScope(filter.scope()),
                activeAt(filter.activeAt()),
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

    private static Specification<AlertEntity> hasOrigin(
            AlertOrigin origin) {

        return (root, query, cb) -> origin == null
                ? cb.conjunction()
                : cb.equal(root.get("origin"), origin);
    }

    private static Specification<AlertEntity> hasScope(
            ScopeType scope) {

        return (root, query, cb) -> {
            if (scope == null) {
                return cb.conjunction();
            }

            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<AlertScopeItemEntity> item = subquery.from(AlertScopeItemEntity.class);
            subquery.select(item.get("alertId"))
                    .where(cb.equal(item.get("scopeType"), scope));

            return root.get("id").in(subquery);
        };
    }

    private static Specification<AlertEntity> activeAt(
            Instant instant) {

        return (root, query, cb) -> {
            if (instant == null) {
                return cb.conjunction();
            }

            Predicate started = cb.lessThanOrEqualTo(
                    root.<Instant>get("activatedAt"), instant);
            Predicate notArchived = cb.or(
                    cb.isNull(root.get("archivedAt")),
                    cb.greaterThan(root.<Instant>get("archivedAt"), instant));

            return cb.and(started, notArchived);
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
