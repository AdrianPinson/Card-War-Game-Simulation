@echo off
setlocal
if defined JAVA_HOME (
  set "WAR_JAVA=%JAVA_HOME%\bin\java.exe"
) else (
  set "WAR_JAVA=java.exe"
)
"%WAR_JAVA%" -cp "%~dp0war-simulation.jar;%~dp0lib\*" war.ui.DesktopLauncher %*
exit /b %ERRORLEVEL%
