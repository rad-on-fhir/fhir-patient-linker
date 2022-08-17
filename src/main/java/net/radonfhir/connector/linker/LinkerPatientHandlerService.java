package net.radonfhir.connector.linker;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IQuery;
import net.radonfhir.connector.base.organization.OrganizationService;
import net.radonfhir.connector.base.subscription.PatientEventHandler;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class LinkerPatientHandlerService implements PatientEventHandler {
    private final OrganizationService organizationService;
    private final Logger logger = LoggerFactory.getLogger(LinkerPatientHandlerService.class);
    private Bundle bundle;

    public LinkerPatientHandlerService(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @Override
    public void patientEvent(IGenericClient client, Patient patient, String event) {
        Optional<Organization> organization = OrganizationService.loadFromReference(client, patient.getManagingOrganization());
        if (organization.isPresent() && !organizationService.isCentral(organization.get())) {
            // Load Central Patient:
            boolean linked = false;
            {
                ArrayList<Patient> centralPatients = linkedCentralPatient(client, patient);
                for (Patient centralPatient : centralPatients) {
                    linked = this.checkLink(centralPatient, patient) | linked;
                }
            }

            // Exit if Linked
            if (linked) return;

            {
                // Similar Patients:
                ArrayList<Patient> similars = searchSimilars(patient);
                for (Patient centralPatient : similars) {
                    linked = this.checkLink(centralPatient, patient) | linked;
                }
            }

            // Exit if Linked
            if (linked) return;

            // Create New:
            this.createCentralAndLink(patient);
        } else {
            this.logger.info("Ignoring Event for Patient " + patient.getId() + " / " + patient.getManagingOrganization());
        }
    }

    private void createCentralAndLink(Patient patient) {
        this.logger.error("Not Implemented: Create Central Patient for Remote:" + patient.getId());
    }

    private boolean checkLink(Patient centralPatient, Patient patient) {
        this.logger.error("Not Implemented: checkLink Central Patient "+centralPatient.getId() + "  and " + patient.getId());
        return false;
    }

    private ArrayList<Patient> searchSimilars(Patient patient) {
        this.logger.error("Not Implemented: searchSimilars for " + patient.getId());
        return new ArrayList<>();
    }

    private static ArrayList<Patient> linkedCentralPatient(IGenericClient client, Patient patient) {
        ArrayList<Patient> list = new ArrayList<>();
        IdType idElement = patient.getIdElement();
        String id = idElement.getId();
        if (id == null) {
            id = idElement.toUnqualified().getIdPart();
        }
        IQuery<Bundle> query = client.search().forResource(Patient.class)
                .where(Patient.LINK.hasId(id))
                .returnBundle(Bundle.class)
                .count(1000);
        Bundle bundle = query.execute();
        if (!bundle.isEmpty()) {
            for (Bundle.BundleEntryComponent bundleEntryComponent : bundle.getEntry()) {
                Resource resource = bundleEntryComponent.getResource();
                if (resource instanceof Patient) {
                    list.add((Patient) resource);
                }
            }
        }
        return list;
    }

    protected List<Patient> loadPatientFromLinks(IGenericClient client, List<Patient.PatientLinkComponent> link) {
        ArrayList<Patient> internalPatients = new ArrayList<>();
        if (link != null) {
            for (Patient.PatientLinkComponent linkComponent : link) {
                String otherId = linkComponent.getOther().getId();
                Patient otherPatient = client.read().resource(Patient.class).withId(otherId).execute();
                if (otherPatient != null) {
                    Optional<Organization> organization = OrganizationService.loadFromReference(client, otherPatient.getManagingOrganization());
                    organization.ifPresent(otherPatient::setManagingOrganizationTarget);
                    internalPatients.add(otherPatient);
                }
            }
        }
        logger.debug("linked Patients found: " + internalPatients.size());
        return internalPatients;
    }
}
