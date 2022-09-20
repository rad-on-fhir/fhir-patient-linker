FROM openjdk:18-jdk-alpine as connector-basic-image
RUN addgroup -S spring && adduser -S spring -G spring
RUN mkdir /rof  && mkdir /rof/config && chown spring:spring /rof -R
FROM connector-basic-image as default
USER spring:spring
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} /rof/app.jar
EXPOSE 8080
VOLUME /rof/config
WORKDIR /rof
ENTRYPOINT ["java","-jar","/rof/app.jar"]