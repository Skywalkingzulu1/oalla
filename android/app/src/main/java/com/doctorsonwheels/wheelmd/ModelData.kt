package com.doctorsonwheels.wheelmd

data class ModelData(
    val name: String,
    val data: ModelDataInfo
)

data class ModelDataInfo(
    val downloads: String,
    val variants: List<ModelVariant>
)

data class ModelVariant(
    val model: String,
    val size: String,
    val context: String,
    val input: String
)

data class ModelSuggestion(
    val displayText: String,
    val modelName: String,
    val size: String,
    val context: String
)
