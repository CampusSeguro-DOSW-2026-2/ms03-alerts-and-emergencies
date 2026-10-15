package edu.eci.dosw.campusseguro.alertas.dto.response;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(
        List<T> contenido,
        int page,
        int size,
        long totalElementos,
        int totalPaginas) {

    public static <T> PageResponse<T> of(List<T> contenido, Page<?> page) {
        return new PageResponse<>(contenido, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
