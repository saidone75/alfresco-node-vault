@echo off
setlocal EnableDelayedExpansion

:: Configuration
set "REPO_DIR=%CD%"
set "JAVADOC_DIR=%REPO_DIR%\target\reports\apidocs"
set "GH_PAGES_DIR=%REPO_DIR%\gh-pages-temp"
set "BRANCH=gh-pages"

:: Get the current branch name
for /f "tokens=*" %%i in ('git rev-parse --abbrev-ref HEAD') do set "ORIGINAL_BRANCH=%%i"

:: Generate Javadoc
echo Generating Javadoc...
call mvn clean javadoc:javadoc
if %ERRORLEVEL% neq 0 (
    echo Error: Maven Javadoc generation failed.
    exit /b 1
)

:: Check if Javadoc directory exists
if not exist "%JAVADOC_DIR%" (
    echo Error: Directory %JAVADOC_DIR% does not exist. Ensure Javadoc generation was successful.
    exit /b 1
)

:: Prepare gh-pages branch
echo Preparing gh-pages branch...
if exist "%GH_PAGES_DIR%" (
    rmdir /s /q "%GH_PAGES_DIR%"
)
mkdir "%GH_PAGES_DIR%"
cd "%GH_PAGES_DIR%"

:: Initialize a new git repository in the temporary directory
git init
:: Add the remote URL from the original repository
for /f "tokens=*" %%i in ('git -C "%REPO_DIR%" remote get-url origin') do set "REMOTE_URL=%%i"
git remote add origin "%REMOTE_URL%"
:: Fetch the gh-pages branch
git fetch origin "%BRANCH%"
:: Checkout gh-pages branch or create it if it doesn't exist
git checkout "%BRANCH%" || git checkout --orphan "%BRANCH%"

:: Copy Javadoc files to gh-pages
echo Copying Javadoc files...
xcopy /s /e /y "%JAVADOC_DIR%\*.*" .

:: Commit changes
git add .
git commit -m "Update Javadoc"
if %ERRORLEVEL% neq 0 (
    echo Error: Git commit failed.
    cd "%REPO_DIR%"
    rmdir /s /q "%GH_PAGES_DIR%"
    exit /b 1
)

:: Push to gh-pages branch
echo Publishing to GitHub Pages...
git push origin "%BRANCH%"
if %ERRORLEVEL% neq 0 (
    echo Error: Git push failed.
    cd "%REPO_DIR%"
    rmdir /s /q "%GH_PAGES_DIR%"
    exit /b 1
)

:: Clean up
echo Cleaning up...
cd "%REPO_DIR%"
rmdir /s /q "%GH_PAGES_DIR%"

:: Switch back to the original branch
echo Switching back to original branch (%ORIGINAL_BRANCH%)...
git checkout "%ORIGINAL_BRANCH%"
if %ERRORLEVEL% neq 0 (
    echo Error: Failed to switch back to %ORIGINAL_BRANCH%.
    exit /b 1
)

echo Javadoc successfully published to GitHub Pages!
endlocal