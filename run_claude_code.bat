@echo off
chcp 65001 >nul
title Claude Code Desktop - Hamza Alfarai
cd /d "%~dp0"
REM Builds ClaudeCode.exe + ClaudeCode_Setup.exe, then launches the setup wizard
py claude_code_suite.py
if errorlevel 1 python claude_code_suite.py
pause
