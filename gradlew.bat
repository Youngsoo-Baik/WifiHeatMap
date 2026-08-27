@rem Gradle startup script for Windows
@echo off
setlocal
set APP_HOME=%~dp0

java -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
if %ERRORLEVEL% equ 0 goto end
exit /b %ERRORLEVEL%

:end
endlocal
