// Copyright (c) 2023 Samsung Electronics Co. LTD. Released under the MIT License.

package com.samsung.depthestimation.data

enum class LayerType {
    HWC,    // Data Dimension is height X width X channel (e.g. Model based on TFLite)
    CHW,    // Data Dimension is channel X height X width (e.g. Model based on Caffe)
    RAW,    // Operation Data Type
}