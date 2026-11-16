@echo off
rem Gera o JAR (precisa de Maven e Java 17+) e abre o Observer Notes.
cd /d "%~dp0"
call mvn -q package -DskipTests
if errorlevel 1 exit /b 1
java -jar target\observer-notes-0.1.0.jar
