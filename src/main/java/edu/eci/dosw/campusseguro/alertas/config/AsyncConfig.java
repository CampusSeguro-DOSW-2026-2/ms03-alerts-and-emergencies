package edu.eci.dosw.campusseguro.alertas.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Hilos que notifican las alertas activadas: cada alerta ocupa uno mientras su lote sale. Si todos están
     * ocupados y la cola se llena, la notificación corre en el hilo que la pidió en vez de descartarse: una alerta
     * nunca se queda sin aviso.
     */
    @Bean(name = "notificationExecutor")
    Executor notificationExecutor(
            @Value("${app.mail.notification-threads:4}") int threads,
            @Value("${app.mail.notification-queue:500}") int queue) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, threads));
        executor.setMaxPoolSize(Math.max(1, threads) * 2);
        executor.setQueueCapacity(Math.max(0, queue));
        executor.setThreadNamePrefix("notification-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
