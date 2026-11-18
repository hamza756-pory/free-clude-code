@echo off
chcp 65001 >nul
title Claude Code Desktop - Python Installer - Hamza Alfarai
echo ==================================================
echo   Claude Code Desktop  -  Designed by Hamza Alfarai
echo ==================================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\install_python.ps1"
pause
