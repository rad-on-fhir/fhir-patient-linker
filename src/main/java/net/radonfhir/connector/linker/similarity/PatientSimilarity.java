package net.radonfhir.connector.linker.similarity;

import org.hl7.fhir.r4.model.Patient;

import java.util.concurrent.CompletableFuture;

public interface PatientSimilarity {
    CompletableFuture<Float> similarity(Patient patA, Patient patB);
}
