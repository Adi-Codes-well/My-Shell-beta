@echo off
REM =====================================
REM Mini Shell launcher in bin folder (expects mini-shell.jar next to this .bat)
REM =====================================

title Mini Shell
color 0A

REM Launch new terminal running the JAR that sits next to this script
start "" cmd /k java --enable-preview -jar "%~dp0mini-shell.jar"

REM Close this launcher cleanly
exit /b
