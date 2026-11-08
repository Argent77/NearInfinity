@echo off
setlocal

cd /d "%~dp0"
java -jar "..\..\..\NearInfinity.jar" --run-tool ImageSequenceToBam "ImageSequenceToBam.json"
if errorlevel 1 (
  echo Conversion failed.
  exit /b 1
)

echo Created BAM from ImageSequenceToBam.json
endlocal
