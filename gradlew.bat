@echo off
rem Velnox wrapper for Windows. Delegates to the Gradle on PATH
rem (CI installs Gradle 8.11.1; see .github/workflows/gradle-build.yml).
gradle %*
