@echo off
rem Starts the CH EMED converter CLI, e.g.:
rem   ch-emed-converter.bat convert --from chmed16a --to ch-emed --author author.json plan.json
rem
rem Requires JAVA_HOME to point to a JDK/JRE 21 or newer (Java 21+ is mandatory; Java 11 or 17 do
rem not work). If JAVA_HOME is not set in your environment, add a line like the following
rem directly below this comment block:
rem   set JAVA_HOME=C:\path\to\jdk-21

if not defined JAVA_HOME goto nojava
if not exist "%JAVA_HOME%\bin\java.exe" goto nojava

"%JAVA_HOME%\bin\java" -jar "%~dp0ch-emed-converter.jar" %*
exit /b %ERRORLEVEL%

:nojava
echo JAVA_HOME is not set or does not contain bin\java.exe. Set JAVA_HOME to a Java 21+ installation 1>&2
echo or add a line 'set JAVA_HOME=C:\path\to\jdk-21' to this script. 1>&2
exit /b 2
