#!/bin/bash

JAVA_OPTS="-Xms80m -Xmx80m -XX:+UseSerialGC -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=0.0.0.0:9000"
export SPRING_PROFILES_ACTIVE=dev

mvn spring-boot:run -Dspring-boot.run.jvmArguments="$JAVA_OPTS" -Dlicense.skip=true
