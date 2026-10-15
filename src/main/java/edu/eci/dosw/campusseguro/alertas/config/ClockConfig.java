package edu.eci.dosw.campusseguro.alertas.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    @Bean
    Clock clock(@Value("${app.timezone:America/Bogota}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
