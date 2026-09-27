package com.example.earthonline.data.local

import androidx.room.TypeConverter
import com.example.earthonline.data.model.CustomField
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 集合/对象类型 <-> JSON 字符串 的 Room 转换器。
 * 对应 HTML 端把数组/对象直接 JSON 化进 state 的做法，保持等价语义。
 */
object Converters {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @TypeConverter
    fun listStringToJson(value: List<String>?): String =
        json.encodeToString(ListSerializer(String.serializer()), value ?: emptyList())

    @TypeConverter
    fun jsonToListString(value: String): List<String> =
        if (value.isBlank()) emptyList()
        else runCatching { json.decodeFromString(ListSerializer(String.serializer()), value) }
            .getOrDefault(emptyList())

    @TypeConverter
    fun customFieldsToJson(value: List<CustomField>?): String =
        json.encodeToString(ListSerializer(CustomField.serializer()), value ?: emptyList())

    @TypeConverter
    fun jsonToCustomFields(value: String): List<CustomField> =
        if (value.isBlank()) emptyList()
        else runCatching { json.decodeFromString(ListSerializer(CustomField.serializer()), value) }
            .getOrDefault(emptyList())
}
