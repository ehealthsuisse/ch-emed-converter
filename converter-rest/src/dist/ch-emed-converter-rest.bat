@echo off
rem Starts the CH EMED converter REST service, e.g.:
rem   ch-emed-converter-rest.bat --server.port=8080 --converter.rest.timeout=30s
rem
rem The service has no authentication and no TLS: run it behind an API gateway or in a protected
rem network only (rest-service.md, "Security model").
rem
rem Requires JAVA_HOME to point to a JDK/JRE 21 or newer (Java 21+ is mandatory; Java 11 or 17 do
rem not work). If JAVA_HOME is not set in your environment, add a line like the following
rem directly below this comment block:
rem   set JAVA_HOME=C:\path\to\jdk-21
rem JVM options (e.g. heap size) can be passed in JAVA_OPTS, e.g. set JAVA_OPTS=-Xmx2g

if not defined JAVA_HOME goto nojava
if not exist "%JAVA_HOME%\bin\java.exe" goto nojava

"%JAVA_HOME%\bin\java" %JAVA_OPTS% -jar "%~dp0ch-emed-converter-rest.jar" %*
exit /b %ERRORLEVEL%

:nojava
echo JAVA_HOME is not set or does not contain bin\java.exe. Set JAVA_HOME to a Java 21+ installation 1>&2
echo or add a line 'set JAVA_HOME=C:\path\to\jdk-21' to this script. 1>&2
exit /b 2
