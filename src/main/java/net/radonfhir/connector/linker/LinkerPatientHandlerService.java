package net.radonfhir.connector.linker;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IQuery;
import net.radonfhir.connector.base.organization.OrganizationService;
import net.radonfhir.connector.base.patient.PatientHelper;
import net.radonfhir.connector.base.subscription.PatientEventHandler;
import net.radonfhir.connector.linker.similarity.PatientSimilarity;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Enumeration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class LinkerPatientHandlerService implements PatientEventHandler {
    private final OrganizationService organizationService;
    private final Logger logger = LoggerFactory.getLogger(LinkerPatientHandlerService.class);
    private final PatientSimilarity patientSimilarity;
    private final float threshold = 0.95f;

    public LinkerPatientHandlerService(OrganizationService organizationService, PatientSimilarity patientSimilarity) {
        this.organizationService = organizationService;
        this.patientSimilarity = patientSimilarity;
    }

    @Override
    public void patientEvent(IGenericClient client, Patient patient, String event) {
        Optional<Organization> organization = OrganizationService.loadFromReference(client, patient.getManagingOrganization());
        if (organization.isPresent() && !organizationService.isCentral(organization.get())) {
            HashSet<Patient> changedPatients = new HashSet<>();
            // Load Central Patient:
            boolean linked = false;
            {
                ArrayList<Patient> centralPatients = linkedCentralPatient(client, patient);
                for (Patient centralPatient : centralPatients) {
                    linked = this.checkLink(client, centralPatient, patient, changedPatients) | linked;
                }
            }

            if (!linked) {
                // Similar Patients:
                ArrayList<Patient> similars = searchSimilars(client, patient);
                this.logger.info("Found Similar Patients to " + PatientHelper.getNameIdStr(patient));
                for (Patient centralPatient : similars) {
                    linked = this.checkLink(client, centralPatient, patient, changedPatients) | linked;
                }
            }

            if (!linked) {
                // Create New:
                this.createCentralAndLink(patient, changedPatients);
            }

            if (changedPatients.size()>0) {
                List<IBaseResource> res = client.transaction().withResources(new ArrayList<>(changedPatients)).execute();
                for (IBaseResource re : res) {
                    this.logger.info("Result Resource " + re.getIdElement().getResourceType() + " as " + re.getIdElement().getValue());
                }
            }
        } else {
            // TODO: if Central Patient CREATED: add Identifier for Central System with FHIR ID as Value
            this.logger.info("Ignoring Event for Patient " + patient.getId() + " / " + patient.getManagingOrganization());
        }
    }

    private void createCentralAndLink(Patient patient, Set<Patient> changedPatients) {
        Patient central = patient.copy();
        central.setId((IIdType) null);
        central.getLink().add(createLinkTo(patient));
        changedPatients.add(central);
        this.logger.error("Added Central Patient '"+PatientHelper.getNameIdStr(central)+"' for Remote: " + PatientHelper.getNameIdStr(patient));
    }

    private boolean checkLink(IGenericClient client, Patient centralPatient, Patient patient, Set<Patient> changedPatients) {
        this.logger.error("checkLink Central Patient "+ PatientHelper.getNameIdStr(centralPatient) + "  and " + PatientHelper.getNameIdStr(patient));
        try {
            float similarity = patientSimilarity.similarity(centralPatient, patient).get();
            boolean isLinked = arePatientLinked(centralPatient, patient);
            if (similarity >= this.threshold && !isLinked) {
                // Add Link
                centralPatient.getLink().add(createLinkTo(patient));
                changedPatients.add(centralPatient);
                return true;
            } else if (similarity< this.threshold && isLinked) {
                // remove Link
                ArrayList<Patient.PatientLinkComponent> remove = new ArrayList<>();
                for (Patient.PatientLinkComponent linkComponent : centralPatient.getLink()) {
                    if (linkComponent.hasOther() && linkComponent.getOther().hasReference()
                            && isReferenceTo(linkComponent.getOther().getReference(), patient)) {
                        remove.add(linkComponent);
                    }
                }
                centralPatient.getLink().removeAll(remove);
                changedPatients.add(centralPatient);
                return false;
            }
            return isLinked;
        } catch (Exception e) {
            throw new RuntimeException("Could not Check Link "+ PatientHelper.getNameIdStr(centralPatient) + "  and " + PatientHelper.getNameIdStr(patient) + ": " + e.toString(), e);
        }
    }

    private boolean isReferenceTo(String reference, Patient patient) {
        return reference != null && patient.hasIdElement()
                && reference.endsWith(idElementToString(patient.getIdElement()));
    }

    @NotNull
    private String idElementToString(IdType idType) {
        if (idType.hasIdPart()) {
            return idType.getResourceType() + "/" + idType.getIdPart();
        } else {
            return idType.toUnqualifiedVersionless().getResourceType() + "/" + idType.toUnqualifiedVersionless().getIdPart();
        }

    }

    private Patient.PatientLinkComponent createLinkTo(Patient patient) {
        Patient.PatientLinkComponent link = new Patient.PatientLinkComponent();
        link.setOther(new Reference(idElementToString(patient.getIdElement())));
        link.setType(Patient.LinkType.SEEALSO);
        return link;
    }

    private boolean arePatientLinked(Patient centralPatient, Patient patient) {
        for (Patient.PatientLinkComponent linkComponent : centralPatient.getLink()) {
            if (linkComponent.hasOther() && linkComponent.getOther().hasReference()
                    && isReferenceTo(linkComponent.getOther().getReference(), patient)) {
                return true;
            }
        }
        return false;
    }

    private ArrayList<Patient> searchSimilars(IGenericClient client, Patient patient) {
        List<String> names = new ArrayList<>();
        HumanName use = null;
        for (HumanName humanName : patient.getName()) {
            if (humanName.hasUse() && humanName.getUse().equals(HumanName.NameUse.USUAL)) {
                use = humanName;
            } else if (use == null && (!humanName.hasUse() || humanName.getUse().equals(HumanName.NameUse.OFFICIAL))) {
                use = humanName;
            }
        }
        if (use == null) return new ArrayList<>();
        use.getGiven().forEach(stringType -> names.add(stringType.getValue()));
        names.add(use.getFamily());
        IQuery<Bundle> query = client.search().forResource(Patient.class)
                .where(Patient.NAME.contains().values(names))
                .and(Patient.BIRTHDATE.exactly().day(patient.getBirthDate()))
                .returnBundle(Bundle.class)
                .count(1000);
        Bundle bundle = query.execute();
        return bundleToPatientList(bundle);
    }

    private static ArrayList<Patient> linkedCentralPatient(IGenericClient client, Patient patient) {
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
        return bundleToPatientList(bundle);
    }

    @NotNull
    private static ArrayList<Patient> bundleToPatientList(Bundle bundle) {
        ArrayList<Patient> list = new ArrayList<>();
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
