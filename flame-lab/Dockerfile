# Base image: a JDK/JRE 17 image that already exists on the local machine.
# Override with: docker build --build-arg BASE_IMAGE=<your-local-jdk17-image> .
ARG BASE_IMAGE=eclipse-temurin:17-jre
FROM ${BASE_IMAGE}

WORKDIR /app
COPY target/flame-lab-1.0.0.jar app.jar

# Jobs are stored in an in-process SQLite database file; mount a volume at /data
# to keep them across container restarts.
ENV FLAMELAB_DB_PATH=/data/flamelab.db
VOLUME /data

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
