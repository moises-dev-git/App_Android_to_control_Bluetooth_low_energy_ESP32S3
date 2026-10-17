package com.example.esp32app.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File

/** Modelo de imagem salvo: recorte de um frame, com a resolução do frame de origem. */
data class TemplateInfo(
    val name: String,
    val imageFile: File,
    val sourceWidth: Int,
    val sourceHeight: Int
)

/**
 * Guarda os modelos de imagem em filesDir/templates como PNG + metadados JSON.
 * A resolução de origem permite reescalar o modelo se a captura mudar de resolução.
 */
class TemplateStore(context: Context) {

    private val dir = File(context.filesDir, "templates").apply { mkdirs() }

    companion object {
        private val VALID_NAME = Regex("^[A-Za-z0-9_\\-]{1,40}$")

        fun isValidName(name: String) = VALID_NAME.matches(name)
    }

    fun list(): List<TemplateInfo> =
        dir.listFiles { f -> f.extension == "png" }
            ?.mapNotNull { get(it.nameWithoutExtension) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    fun get(name: String): TemplateInfo? {
        val png = File(dir, "$name.png")
        if (!png.exists()) return null
        val meta = File(dir, "$name.json")
        val json = runCatching { JSONObject(meta.readText()) }.getOrNull()
        return TemplateInfo(
            name = name,
            imageFile = png,
            sourceWidth = json?.optInt("sourceWidth") ?: 0,
            sourceHeight = json?.optInt("sourceHeight") ?: 0
        )
    }

    fun loadBitmap(info: TemplateInfo): Bitmap? = BitmapFactory.decodeFile(info.imageFile.absolutePath)

    fun save(name: String, crop: Bitmap, sourceWidth: Int, sourceHeight: Int) {
        require(isValidName(name)) { "Nome inválido: use letras, números, _ ou - (até 40)" }
        File(dir, "$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(dir, "$name.json").writeText(
            JSONObject().put("sourceWidth", sourceWidth).put("sourceHeight", sourceHeight).toString()
        )
    }

    fun delete(name: String) {
        File(dir, "$name.png").delete()
        File(dir, "$name.json").delete()
    }
}
