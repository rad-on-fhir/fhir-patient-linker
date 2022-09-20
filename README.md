# Fhir Patient Linker

Compare and Link external Patients based on Rules

## Configuration

```
# The Connector needs Events from the FHIR Repository. You can use the 
# build in Subscriptions - or the MQTT-Connector. Whatever your Repository is configured to.
#
# !! ONLY USE MQTT OR SUBSCRIPTION !!
#
# if you prefer the Build in Subscriptions:
fhir.subscription.enabled=false
# Where can the FHIR Server reach THIS instance for Events:
fhir.subscription.baseUrl=http://192.168.0.2:8080
# If you want to use MQTT-Event transfer, add your Server and Host:
mqtt.enabled=true
mqtt.host=192.168.0.1

# Remtote FHIR Server URL:
fhir.serverBaseUrl=http://192.168.0.1:8080/fhir

# Configure the Organisation Struckture:
# The ID of the CENTRAL Patient Organisation:
fhir.organisation.centralID=2
# Since the Linker manages the CENTRAL, this is the Same value (will be fixed)
fhir.organisation.myID=2
```

## Run inside a Docker

```console
docker run -d \
    --restart unless-stopped
    --name fhir-patient-linker
    -p 28080:8080 \    
    -v /your/path/to/folder/with/config/:/rof/config \
     srcblk/rad-on-fhir-patient-linker
```

Let's unpack this:
- -d: start as deamon
- restart: autostart on startup and auto-restart container on fail
- name this container - but run only one Linker per Repository!
- -p: expose REST-Api at Port 28080 on host
- -v: the Service need an additional config as "application.properties". create a
  File and Link the folder into the Image as /rof/config (so that it will be /rof/config/application.properties)