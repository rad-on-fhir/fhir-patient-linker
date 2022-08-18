package net.radonfhir.connector.linker.similarity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class SimilarityConfig {

    @Primary
    @Bean
    public PatientSimilarity patientSimilarity() {
        return new NameOnlyPatientSimilarity();
    }
}
