package edu.eci.dosw.campusseguro.alertas.security.local;

import edu.eci.dosw.campusseguro.alertas.security.Role;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
@RequestMapping("/local/tokens")
public class LocalTokenController {

    private static final String GENERIC_USER = "USER";

    private final LocalTokenService localTokenService;

    public LocalTokenController(LocalTokenService localTokenService) {
        this.localTokenService = localTokenService;
    }

    @GetMapping
    public Map<String, String> tokens() {
        Map<String, String> tokens = new LinkedHashMap<>();
        for (Role role : Role.values()) {
            String subject = "local-" + role.name().toLowerCase(Locale.ROOT);
            tokens.put(role.name(), localTokenService.generate(subject, List.of(role.name())));
        }
        tokens.put(GENERIC_USER, localTokenService.generate("local-user", List.of()));
        return tokens;
    }
}
