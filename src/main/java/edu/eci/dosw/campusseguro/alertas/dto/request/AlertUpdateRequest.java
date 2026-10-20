
package edu.eci.dosw.campusseguro.alertas.dto.request;

import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertType;
import edu.eci.dosw.campusseguro.alertas.validation.AtLeastOneField;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

@AtLeastOneField
public record AlertUpdateRequest(
        AlertType type,

        @Valid
        ScopeRequest scope,

        @Size(max = 1000, message = "Instructions cannot exceed 1000 characters.")
        @Pattern(
                regexp = ".*\\S.*",
                flags = Pattern.Flag.DOTALL,
                message = "Instructions cannot be empty"
        )
        String instructions,

        @Size(min = 1, message = "You must specify at least one recipient.")
        List<
                @NotNull(message = "The recipient cannot be null")
                @Valid RecipientRequest
        > recipients
) {
    public boolean hasAnyField() {
        return type != null
                || scope != null
                || instructions != null
                || recipients != null;
    }
}
