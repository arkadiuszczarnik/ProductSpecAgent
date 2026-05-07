@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "TARGET=windows-amd64"
set "GZIP_PATH=%SCRIPT_DIR%%TARGET%\living-sync-reporter.exe.gz"
set "CACHE_DIR=%SCRIPT_DIR%.cache\%TARGET%"
set "BINARY_PATH=%CACHE_DIR%\living-sync-reporter.exe"

if not exist "%GZIP_PATH%" (
  echo Missing Living Sync reporter binary: %GZIP_PATH% 1>&2
  exit /b 1
)

if not exist "%BINARY_PATH%" (
  mkdir "%CACHE_DIR%" >nul 2>nul
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$in=[IO.File]::OpenRead('%GZIP_PATH%'); $out=[IO.File]::Create('%BINARY_PATH%'); $gz=New-Object IO.Compression.GzipStream($in,[IO.Compression.CompressionMode]::Decompress); $gz.CopyTo($out); $gz.Dispose(); $out.Dispose(); $in.Dispose()"
  if errorlevel 1 exit /b %ERRORLEVEL%
)

"%BINARY_PATH%" %*
exit /b %ERRORLEVEL%
