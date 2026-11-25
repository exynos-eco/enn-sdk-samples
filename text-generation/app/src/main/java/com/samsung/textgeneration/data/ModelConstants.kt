package com.samsung.textgeneration.data

object ModelConstants {
    const val BASE_DIR = "/data/local/tmp/qwen3_0_6b"
    const val MODEL_DIR = "$BASE_DIR/models"
    const val COMMON_DIR = "$BASE_DIR/common"
    const val PREFILL_P1_MODEL = "Qwen3_0.6B_Prefill_1_simplify_O1_SingleCore.nnc"
    const val PREFILL_P2_MODEL = "Qwen3_0.6B_Prefill_2_simplify_O1_SingleCore.nnc"
    const val DECODE_MODEL = "Qwen3_0.6B_Decode_simplify_O1_SingleCore.nnc"

    const val EMBEDDING_BIN = "embedding.bin"
    const val LM_HEAD_BIN = "lm_head_weight.bin"

    const val COS_PREFILL_BIN = "cos_prefill.bin"
    const val SIN_PREFILL_BIN = "sin_prefill.bin"
    const val COS_DECODE_BIN = "cos_decode.bin"
    const val SIN_DECODE_BIN = "sin_decode.bin"

    const val TOKENIZER_JSON = "tokenizer.json"
    const val TOKENIZER_CONFIG_JSON = "tokenizer_config.json"
    const val VOCAB_JSON = "vocab.json"

    const val PREFILL_SEQ = 128
    const val DECODE_TOTAL_LEN = 129
    const val MAX_CACHE_LEN = 128

    const val HIDDEN = 1024
    const val VOCAB = 151936
    const val NUM_LAYERS = 28
    const val SPLIT_INDEX = 14
    const val NUM_KV_HEADS = 8
    const val HEAD_DIM = 128
    const val MAX_NEW_TOKENS = 128

    val FIXED_PROMPT_IDS = intArrayOf(
        151644, 872, 198, 3838, 374, 264, 3460, 4128, 1614, 30, 151645, 198, 151644, 77091, 198, 151667, 271, 151668, 271
    )

    const val PAD_TOKEN_ID = 151643
}
