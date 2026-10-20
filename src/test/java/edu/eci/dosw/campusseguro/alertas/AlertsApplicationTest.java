
package edu.eci.dosw.campusseguro.alertas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class AlertsApplicationTest {

    public static void main(String[] args) {
        SpringApplication.run(AlertsApplication.class, args);
    }
}
