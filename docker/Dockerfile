ARG ACS_IMAGE
ARG ACS_VERSION
FROM ${ACS_IMAGE}:${ACS_VERSION}

ARG TOMCAT_DIR=/usr/local/tomcat

USER root

COPY model/anv-content-model.xml $TOMCAT_DIR/shared/classes/alfresco/extension/
COPY model/anv-model-context.xml $TOMCAT_DIR/shared/classes/alfresco/extension/