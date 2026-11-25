@echo off
set "JAVA_HOME=C:\Program Files\Java\jdk-1.8"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%~dp0"
"%JAVA_HOME%\bin\java.exe" -jar target\ai-shopping-mall-1.0.0.jar > run.log 2>&1
