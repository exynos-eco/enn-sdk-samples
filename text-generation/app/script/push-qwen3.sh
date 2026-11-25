#!/bin/bash

echo "==== Qwen3 Push Script Start ===="

adb root
adb remount

BASE_DIR="/data/local/tmp/qwen3_0_6b"
MODEL_DIR="${BASE_DIR}/models"
COMMON_DIR="${BASE_DIR}/common"

echo "[1] Create directories..."
adb shell "rm -rf ${BASE_DIR}"
adb shell "mkdir -p ${MODEL_DIR}"
adb shell "mkdir -p ${COMMON_DIR}"

echo "[2] Push NNC models..."
adb push ../materials/models/Qwen3_0.6B_Prefill_1_simplify_O2_SingleCore.nnc ${MODEL_DIR}/
adb push ../materials/models/Qwen3_0.6B_Prefill_2_simplify_O2_SingleCore.nnc ${MODEL_DIR}/
adb push ../materials/models/Qwen3_0.6B_Decode_simplify_O2_SingleCore.nnc ${MODEL_DIR}/

echo "[3] Push BIN files..."
adb push ../materials/common/embedding.bin ${COMMON_DIR}/
adb push ../materials/common/lm_head_weight.bin ${COMMON_DIR}/
adb push ../materials/common/cos_prefill.bin ${COMMON_DIR}/
adb push ../materials/common/sin_prefill.bin ${COMMON_DIR}/
adb push ../materials/common/cos_decode.bin ${COMMON_DIR}/
adb push ../materials/common/sin_decode.bin ${COMMON_DIR}/

echo "[4] Verify files..."
adb shell "ls -lh ${MODEL_DIR}"
adb shell "ls -lh ${COMMON_DIR}"

adb shell sync

echo "==== Qwen3 Push Done ===="