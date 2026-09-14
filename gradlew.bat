@rem Standard Gradle wrapper launcher script for Windows.
@rem NOTE: gradle-wrapper.jar is not bundled (binary). Run "gradle wrapper" locally
@rem or open the project in Android Studio to generate it automatically.
@echo off
set APP_HOME=%~dp0
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
if not exist "%CLASSPATH%" (
  echo gradle-wrapper.jar not found. Run "gradle wrapper --gradle-version 8.7" first,
  echo or open this project in Android Studio to have it generated automatically.
  exit /b 1
)
java -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
