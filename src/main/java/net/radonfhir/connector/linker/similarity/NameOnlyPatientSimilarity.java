package net.radonfhir.connector.linker.similarity;

import net.radonfhir.connector.base.patient.PatientHelper;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Patient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.CompletableFuture;

public class NameOnlyPatientSimilarity implements PatientSimilarity {

    public static final DateFormat DATE_FORMAT = DateFormat.getDateInstance();
    private final Logger logger = LoggerFactory.getLogger(NameOnlyPatientSimilarity.class);

    @Override
    public CompletableFuture<Float> similarity(Patient patA, Patient patB) {
        return CompletableFuture.supplyAsync(() -> {
            if (
                    !patA.hasBirthDate()
                    || !patB.hasBirthDate()
                    || !sameDay(patA.getBirthDate(), patB.getBirthDate())
            ) {
                return 0.001f; // no Birthday - or not the same?
            }
            if (!isSilimarGender(patA.getGender(), patB.getGender())) {
                logger.info("GENDER Missmatch: " + patA.getGender() + " and " + patB.getGender());
                return 0.002f;
            }
            HumanName useA = PatientHelper.getMainHumanName(patA);
            HumanName useB = PatientHelper.getMainHumanName(patB);
            if (useB==null || useA==null) {
                return 0.003f; // no Name? Not Similar...
            }
            if (
                    useA.getFamily().equals(useB.getFamily())
                            && useA.getGivenAsSingleString().equals(useB.getGivenAsSingleString())
            ) {
                return 1.0f;
            }
            float family = levenstein(useA.getFamily(), useB.getFamily());
            float given =  levenstein(useA.getGivenAsSingleString(), useB.getGivenAsSingleString());
            if (family < 0.7 || given < 0.7) {
                return 0.004f;
            }
            return Math.min(family,given);
        });
    }

    private float levenstein(String strA, String strB) {
        LevenshteinDistance defaultInstance = LevenshteinDistance.getDefaultInstance();
        int d = defaultInstance.apply(strA, strB);
        int p = Math.min(1-Math.floorDiv(d, strA.length()), 1-Math.floorDiv(d, strB.length()));
        if (p <0.75 && d > 0) {
            return 0.0f;
        }
        return p;
    }

    private boolean isSilimarGender(Enumerations.AdministrativeGender genderA, Enumerations.AdministrativeGender genderB) {
        if (genderA==null || genderB == null) return false;
        return genderA.toCode().equalsIgnoreCase(genderB.toCode());
    }

    private boolean sameDay(Date dA, Date dB) {
        if (dA == null | dB == null) return false;
        // TODO: Check for a better compare on DATE - Basis. It's a "Birthday" without a TIME... "01.01.2022 16:03" == "01.01.2022 11:02"
        return DATE_FORMAT.format(dA).equals(DATE_FORMAT.format(dB));
    }
}
