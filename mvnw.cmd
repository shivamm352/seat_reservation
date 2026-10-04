@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements. See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership. The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License. You may obtain a copy of the License at
@REM
@REM http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied. See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.2.0
@REM ----------------------------------------------------------------------------

@IF "%__MVNW_ARG0_NAME__%"=="" (SET "__MVNW_ARG0_NAME__=%~nx0")
@SET @@MVNW_LAUNCHER_CLASS=org.apache.maven.wrapper.MavenWrapperMain

@SETLOCAL

@SET MAVEN_PROJECTBASEDIR=%~dp0

@SET WRAPPER_JAR="%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar"

@SET DOWNLOAD_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar

@IF EXIST %WRAPPER_JAR% (
    GOTO run
)

@ECHO Downloading Maven Wrapper...
@IF NOT "%MVNW_VERBOSE%"=="" ECHO %DOWNLOAD_URL%
powershell -Command "&{ try { Invoke-WebRequest -UseBasicParsing '%DOWNLOAD_URL%' -OutFile %WRAPPER_JAR%; } catch { $err = $_.Exception; Write-Host 'The Maven Wrapper downloader failed:' $err.Message; exit 1; }}"
IF %ERRORLEVEL% NEQ 0 GOTO error

:run
@SET JAVA_HOME_CANDIDATE=%JAVA_HOME%
@IF "%JAVA_HOME_CANDIDATE%"=="" (
  FOR /F "usebackq tokens=*" %%i IN (`where java 2^>nul`) DO (
    SET "__JAVA_PARENT=%%~dpi.."
    CALL SET "JAVA_HOME_CANDIDATE=!__JAVA_PARENT!"
    GOTO :java_found
  )
)
:java_found

@SET JAVA_EXECUTABLE=%JAVA_HOME_CANDIDATE%\bin\java.exe
@IF NOT EXIST "%JAVA_EXECUTABLE%" SET JAVA_EXECUTABLE=java

@SET MAVEN_OPTS=%MAVEN_OPTS% "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%"

@"%JAVA_EXECUTABLE%" %MAVEN_OPTS% ^
  -classpath %WRAPPER_JAR% ^
  "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" ^
  org.apache.maven.wrapper.MavenWrapperMain %*

IF %ERRORLEVEL% NEQ 0 GOTO error
GOTO end

:error
SET ERROR_CODE=%ERRORLEVEL%

:end
@ENDLOCAL & SET ERROR_CODE=%ERROR_CODE%

EXIT /B %ERROR_CODE%
