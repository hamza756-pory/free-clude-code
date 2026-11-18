@echo off
chcp 65001 >nul
title Hamza Link - PC + Phone - Hamza Alfarai
cd /d "%~dp0hamza_link"
REM Starts the Hamza Link server and opens the PC panel (scan the QR code with your phone)
py hamza_link.py %*
if errorlevel 9009 python hamza_link.py %*
pause
