# syntax=docker/dockerfile:1.2

ARG MAVEN_VERSION=3-sapmachine-21
ARG JAVA_VERSION=21-jre-headless-latest

FROM maven:${MAVEN_VERSION} as builder

ENV MAVEN_CLI_OPTS="-s .m2/settings.xml -Dmaven.repo.local=.m2/repository --batch-mode"
ENV MAVEN_OPTS="-client -XX:+TieredCompilation -XX:TieredStopAtLevel=1"

WORKDIR /application

# Optimizing for dependencies cache

COPY .m2/settings.xml ./.m2/settings.xml

COPY pom.xml ./pom.xml

RUN mvn $MAVEN_CLI_OPTS -e -B de.qaware.maven:go-offline-maven-plugin:resolve-dependencies

# Building

COPY ./ ./

RUN mvn $MAVEN_CLI_OPTS install -DskipTests -Dlicense.skip=true

FROM azul/zulu-openjdk-alpine:${JAVA_VERSION}
ARG UID=1010
ARG GID=1010

RUN apk add curl jq

RUN addgroup --gid ${GID} -S spring && adduser --uid ${UID} -S spring -G spring

USER spring:spring
ARG JAR_FILE=/application/target/anv.jar
COPY --from=builder ${JAR_FILE} anv.jar

ENV SPRING_PROFILES_ACTIVE docker
ENV APPLICATION_BASE_URL http://127.0.0.1:10501
ENV SHARED_FILES_PATH /tmp/anv
ENV CONNECTION_TIMEOUT 10000
ENV READ_TIMEOUT 10000

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MinRAMPercentage=35", "-XX:MaxRAMPercentage=70", "-jar","/anv.jar"]