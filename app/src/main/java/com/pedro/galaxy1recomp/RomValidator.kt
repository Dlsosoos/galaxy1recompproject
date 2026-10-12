package com.pedro.galaxy1recomp

import android.content.Context
import android.net.Uri

data class RomInfo(val ok: Boolean, val format: String, val id: String, val region: String, val msg: String)

object RomValidator {
    private const val WII_MAGIC = 0x5D1C9EA3
    private val regions = mapOf('E' to "NTSC-U (EUA)", 'P' to "PAL (Europa)", 'J' to "NTSC-J (Japão)", 'K' to "NTSC-K (Coreia)")

    fun inspect(ctx: Context, uri: Uri): RomInfo = try {
        val buf = ByteArray(0x240)
        var n = 0
        ctx.contentResolver.openInputStream(uri)?.use { s ->
            while (n < buf.size) { val r = s.read(buf, n, buf.size - n); if (r < 0) break; n += r }
        }
        if (n < buf.size) RomInfo(false, "", "", "", "Arquivo pequeno demais ou ilegível")
        else {
            val wbfs = String(buf, 0, 4, Charsets.US_ASCII) == "WBFS"
            val off = if (wbfs) 0x200 else 0           // WBFS guarda cópia do cabeçalho do disco em 0x200
            val id = String(buf, off, 6, Charsets.US_ASCII)
            val magic = ((buf[off + 0x18].toInt() and 0xFF) shl 24) or ((buf[off + 0x19].toInt() and 0xFF) shl 16) or
                ((buf[off + 0x1A].toInt() and 0xFF) shl 8) or (buf[off + 0x1B].toInt() and 0xFF)
            val fmt = if (wbfs) "wbfs" else "iso"
            when {
                magic != WII_MAGIC -> RomInfo(false, fmt, id, "", "Não é um disco de Wii válido")
                !id.startsWith("RMG") -> RomInfo(false, fmt, id, "", "Disco de Wii, mas não é Super Mario Galaxy 1 (ID $id)")
                else -> RomInfo(true, fmt, id, regions[id[3]] ?: "Desconhecida", "Super Mario Galaxy reconhecido")
            }
        }
    } catch (e: Exception) {
        RomInfo(false, "", "", "", "Erro ao ler: ${e.message}")
    }
}
