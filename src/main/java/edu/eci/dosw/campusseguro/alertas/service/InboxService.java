package edu.eci.dosw.campusseguro.alertas.service;

import edu.eci.dosw.campusseguro.alertas.dto.request.InboxStatus;
import edu.eci.dosw.campusseguro.alertas.dto.response.InboxAlertResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.PageResponse;
import edu.eci.dosw.campusseguro.alertas.security.AccessActor;
import java.util.UUID;

public interface InboxService {

    PageResponse<InboxAlertResponse> inbox(
            InboxStatus status,
            Integer page,
            Integer size,
            AccessActor actor);
}
