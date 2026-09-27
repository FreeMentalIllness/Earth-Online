package com.example.earthonline.data.local.entity

import android.util.Base64
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** ByteArray? 的 Base64 序列化器：用于 ProfileEntity.avatarData 备份导出/导入 */
object Base64BytesSerializer : KSerializer<ByteArray?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ByteArray?", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ByteArray?) {
        val s = value?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: ""
        encoder.encodeString(s)
    }

    override fun deserialize(decoder: Decoder): ByteArray? {
        val s = decoder.decodeString()
        if (s.isEmpty()) return null
        return Base64.decode(s, Base64.NO_WRAP)
    }
}
