package br.com.coti.conhecimento;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ApiConhecimentoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiConhecimentoApplication.class, args);
    }
}
