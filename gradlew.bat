@echo off
setlocal
set APP_DIR=%~dp0
if defined JAVA_HOME (
    set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA_BIN=java.exe"
)
"%JAVA_BIN%" -classpath "%APP_DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
endlocal
