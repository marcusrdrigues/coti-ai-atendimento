package br.com.coti.atendimentos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ApiAtendimentosApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiAtendimentosApplication.class, args);
    }
}
