package edu.eci.dosw.campusseguro.alertas.service.impl;

import edu.eci.dosw.campusseguro.alertas.dto.request.InboxStatus;
import edu.eci.dosw.campusseguro.alertas.dto.response.InboxAlertResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.PageResponse;
import edu.eci.dosw.campusseguro.alertas.entity.AlertConfirmationEntity;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.AlertScopeItemEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.RecipientType;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidPaginationException;
import edu.eci.dosw.campusseguro.alertas.mapper.AlertMapper;
import edu.eci.dosw.campusseguro.alertas.repository.AlertConfirmationRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertScopeItemRepository;
import edu.eci.dosw.campusseguro.alertas.security.AccessActor;
import edu.eci.dosw.campusseguro.alertas.service.InboxService;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InboxServiceImpl implements InboxService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final AlertRepository alertRepository;
    private final AlertScopeItemRepository scopeItemRepository;
    private final AlertConfirmationRepository confirmationRepository;
    private final AlertMapper alertMapper;

    public InboxServiceImpl(
            AlertRepository alertRepository,
            AlertScopeItemRepository scopeItemRepository,
            AlertConfirmationRepository confirmationRepository,
            AlertMapper alertMapper) {
        this.alertRepository = alertRepository;
        this.scopeItemRepository = scopeItemRepository;
        this.confirmationRepository = confirmationRepository;
        this.alertMapper = alertMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InboxAlertResponse> inbox(
            InboxStatus status,
            Integer page,
            Integer size,
            AccessActor actor) {

        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        int resolvedSize = size == null ? DEFAULT_PAGE_SIZE : size;

        validatePagination(resolvedPage, resolvedSize);

        Collection<AlertStatus> statuses = statuses(status);
        Set<String> roles = actor.roles().isEmpty()
                ? Set.of("__NO_ROLE__")
                : actor.roles();

        Pageable pageable = PageRequest.of(
                resolvedPage,
                resolvedSize,
                Sort.by(
                        Sort.Order.desc("activatedAt").nullsLast(),
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")));

        Page<AlertEntity> result = alertRepository.findInbox(
                actor.userId(),
                roles,
                statuses,
                RecipientType.USER,
                RecipientType.ROLE,
                pageable);

        List<UUID> alertIds = result.getContent().stream()
                .map(AlertEntity::getId)
                .toList();

        Map<UUID, List<AlertScopeItemEntity>> scopeByAlert =
                alertIds.isEmpty()
                        ? Map.of()
                        : scopeItemRepository.findByAlertIdIn(alertIds)
                                .stream()
                                .collect(Collectors.groupingBy(
                                        AlertScopeItemEntity::getAlertId));

        Map<UUID, AlertConfirmationEntity> confirmations =
                alertIds.isEmpty()
                        ? Map.of()
                        : confirmationRepository
                                .findByUserIdAndAlertIdIn(
                                        actor.userId(),
                                        alertIds)
                                .stream()
                                .collect(Collectors.toMap(
                                        AlertConfirmationEntity::getAlertId,
                                        Function.identity()));

        List<InboxAlertResponse> content = result.getContent()
                .stream()
                .map(alert -> new InboxAlertResponse(
                        alert.getId(),
                        alertMapper.sortedTypes(alert.getTypes()),
                        alert.getTitle(),
                        alert.getStatus(),
                        alert.getOrigin(),
                        alertMapper.toScope(
                                scopeByAlert.getOrDefault(
                                        alert.getId(),
                                        List.of())),
                        alert.getInstructions(),
                        alert.getActivatedAt(),
                        alert.getArchivedAt(),
                        confirmation(confirmations.get(alert.getId()))))
                .toList();

        return PageResponse.of(content, result);
    }

    private Collection<AlertStatus> statuses(InboxStatus status) {
        InboxStatus resolved = status == null
                ? InboxStatus.ACTIVE
                : status;

        return switch (resolved) {
            case ACTIVE -> EnumSet.of(AlertStatus.ACTIVE);
            case ARCHIVED -> EnumSet.of(AlertStatus.ARCHIVED);
            case ALL -> EnumSet.of(
                    AlertStatus.ACTIVE,
                    AlertStatus.ARCHIVED);
        };
    }

    private java.time.Instant confirmation(
            AlertConfirmationEntity confirmation) {

        return confirmation == null
                ? null
                : confirmation.getConfirmedAt();
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new InvalidPaginationException(
                    "page",
                    "Page index cannot be negative");
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidPaginationException(
                    "size",
                    "Page size must be between 1 and "
                            + MAX_PAGE_SIZE);
        }
    }
}
