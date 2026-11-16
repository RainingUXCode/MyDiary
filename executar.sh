#!/bin/sh
# Gera o JAR (precisa de Maven e Java 17+) e abre o Observer Notes.
cd "$(dirname "$0")" && mvn -q package -DskipTests && java -jar target/observer-notes-0.1.0.jar
