@echo off
rem kof — Kof platform launcher (Windows).
rem Uses the embedded OpenJDK shipped with the distribution when present.
rem Falls back to a system `java` only in development builds.

setlocal
set "KOF_BIN=%~dp0"
set "KOF_HOME=%KOF_BIN%.."

if not exist "%KOF_HOME%\lib\kof.jar" (
    echo kof: distribution incomplete - missing lib\kof.jar
    echo kof: build it with: mvn clean package -DskipTests ^&^& scripts\package.sh
    exit /b 1
)

set "JAVA=java"
set "EMBEDDED=false"
if exist "%KOF_HOME%\jdk\bin\java.exe" (
    set "JAVA=%KOF_HOME%\jdk\bin\java.exe"
    set "EMBEDDED=true"
)

"%JAVA%" -Dkof.install.dir="%KOF_HOME%" -Dkof.embedded.jdk="%EMBEDDED%" -jar "%KOF_HOME%\lib\kof.jar" %*
exit /b %ERRORLEVEL%