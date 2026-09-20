@echo off
rem Convenience wrapper so the build script can be run by double-click or from cmd.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-all.ps1" %*
