@echo off

rem formatlist.bat: a batch file for listing supported formats in Bio-Formats

rem Required JARs: loci_tools.jar

setlocal
set SCIFIO_DIR=%~dp0
if "%SCIFIO_DIR:~-1%" == "\" set SCIFIO_DIR=%SCIFIO_DIR:~0,-1%

set SCIFIO_PROG=loci.formats.tools.PrintFormatTable
call "%SCIFIO_DIR%\scifio.bat" %*
