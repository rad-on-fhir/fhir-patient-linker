package net.radonfhir.connector.linker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"net.radonfhir.connector"})
@ConfigurationPropertiesScan(basePackages = {"net.radonfhir.connector"})
@EnableConfigurationProperties()
public class PatientLinkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PatientLinkerApplication.class, args);
    }


}
