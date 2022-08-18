package net.radonfhir.connector.linker.similarity;

import org.hl7.fhir.r4.model.Patient;

import java.util.concurrent.CompletableFuture;

public class NameOnlyPatientSimilarity implements PatientSimilarity {

    @Override
    public CompletableFuture<Float> similarity(Patient patA, Patient patB) {
        return CompletableFuture.supplyAsync(() -> 0.7f);
    }
}
