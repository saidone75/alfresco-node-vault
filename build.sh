#!/bin/bash

DIST_DIR=anp

if [ -e $DIST_DIR ]; then rm -rf $DIST_DIR; fi
mkdir -p $DIST_DIR/log
mkdir -p $DIST_DIR/config
mvn package -DskipTests -Dlicense.skip=true
cp target/anp.jar $DIST_DIR
cp src/main/resources/application.yml $DIST_DIR/config
cp src/main/resources/example*.json $DIST_DIR