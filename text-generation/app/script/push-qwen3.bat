@echo off
setlocal EnableExtensions

echo ==== Qwen3 Push Script Start ====

cd /d "%~dp0"

where adb >nul 2>&1
if errorlevel 1 (
    echo [ERROR] adb not found
    pause
    exit /b 1
)

adb devices

echo.
echo [0] adb root / remount...
adb root
adb remount

set "BASE_DIR=/data/local/tmp/qwen3_0_6b"
set "MODEL_DIR=%BASE_DIR%/models"
set "COMMON_DIR=%BASE_DIR%/common"

set "LOCAL_MODEL_DIR=..\materials\models"
set "LOCAL_COMMON_DIR=..\materials\common"

echo.
echo [1] Create directories...
adb shell "rm -rf %BASE_DIR%"
adb shell "mkdir -p %MODEL_DIR%"
adb shell "mkdir -p %COMMON_DIR%"

echo.
echo [2] Push NNC models...
call :push "%LOCAL_MODEL_DIR%\Qwen3_0.6B_Prefill_1_simplify_O1_SingleCore.nnc" "%MODEL_DIR%/"
call :push "%LOCAL_MODEL_DIR%\Qwen3_0.6B_Prefill_2_simplify_O1_SingleCore.nnc" "%MODEL_DIR%/"
call :push "%LOCAL_MODEL_DIR%\Qwen3_0.6B_Decode_simplify_O1_SingleCore.nnc" "%MODEL_DIR%/"

echo.
echo [3] Push BIN files...
call :push "%LOCAL_COMMON_DIR%\embedding.bin" "%COMMON_DIR%"
call :push "%LOCAL_COMMON_DIR%\cos_prefill.bin" "%COMMON_DIR%"
call :push "%LOCAL_COMMON_DIR%\sin_prefill.bin" "%COMMON_DIR%"
call :push "%LOCAL_COMMON_DIR%\cos_decode.bin" "%COMMON_DIR%"
call :push "%LOCAL_COMMON_DIR%\sin_decode.bin" "%COMMON_DIR%"
call :push "%LOCAL_COMMON_DIR%\lm_head_weight.bin" "%COMMON_DIR%"

echo.
echo [5] Push tokenizer files...
call :push "%LOCAL_COMMON_DIR%\tokenizer.json" "%COMMON_DIR%"
call :push "%LOCAL_COMMON_DIR%\tokenizer_config.json" "%COMMON_DIR%"
call :push "%LOCAL_COMMON_DIR%\vocab.json" "%COMMON_DIR%"

echo.
echo [6] Verify files...
adb shell "ls -lh %MODEL_DIR%"
adb shell "ls -lh %COMMON_DIR%"

adb shell sync

echo.
echo ==== DONE ====
pause
exit /b 0

:push
set "SRC=%~1"
set "DST=%~2"

if not exist "%SRC%" (
    echo [ERROR] file not found: %SRC%
    exit /b 1
)

adb push "%SRC%" "%DST%"
if errorlevel 1 (
    echo [ERROR] push failed: %SRC%
    exit /b 1
)

exit /b 0