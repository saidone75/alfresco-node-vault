#!/bin/bash

DIST_DIR=anv

if [ -e $DIST_DIR ]; then rm -rf $DIST_DIR; fi
mkdir -p $DIST_DIR/log
mkdir -p $DIST_DIR/config
mvn clean package -DskipTests -Dlicense.skip=true || exit $?
cp target/anv-*.jar $DIST_DIR
cp src/main/resources/application.yml $DIST_DIR/config