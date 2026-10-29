@echo off
title ORBIT AI USB helper
echo === ORBIT AI (Windows) ===
echo Searching USB drives for OrbitAI.apk ...

set FOUND=
for %%d in (D E F G H I J K L M N) do (
  if exist "%%d:\OrbitAI\OrbitAI.apk" (
    set FOUND=%%d:\OrbitAI\OrbitAI.apk
    goto :copy
  )
  if exist "%%d:\OrbitAI.apk" (
    set FOUND=%%d:\OrbitAI.apk
    goto :copy
  )
  if exist "%%d:\OrbitAI\app-debug.apk" (
    set FOUND=%%d:\OrbitAI\app-debug.apk
    goto :copy
  )
)

echo OrbitAI.apk not found on D:–N:
pause
exit /b 1

:copy
echo Found: %FOUND%
if not exist "%USERPROFILE%\Downloads" mkdir "%USERPROFILE%\Downloads"
copy /Y "%FOUND%" "%USERPROFILE%\Downloads\OrbitAI.apk"
echo Copied to Downloads\OrbitAI.apk
echo Transfer this APK to your Android phone and open it to install.
explorer "%USERPROFILE%\Downloads"
pause
