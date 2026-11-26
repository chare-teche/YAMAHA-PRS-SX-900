package com.example.formats

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream

/** What a file in a user folder is, judged by name and (for styles) content. */
enum class Kind(val label: String, val supported: Boolean) {
    YAMAHA_STY("STY", true),
    ORG_STYLE("ORG", false),
    MIDI("MID", false),
    PAD("PAD", false),
    REGIST("RGT", false),
    SET("SET", false),
    INSTRUMENT("VOICE", false),
    FOLDER("DIR", true),
    OTHER("", false)
}

class LibEntry(val name: String, val docId: String, val isDir: Boolean, val kind: Kind)

object Library {
    fun kindOf(name: String): Kind {
        val n = name.lowercase()
        val ext = n.substringAfterLast('.', "")
        return when (ext) {
            "sty", "prs", "bcs" -> Kind.YAMAHA_STY
            "style", "template", "org", "music" -> Kind.ORG_STYLE
            "mid", "midi", "kar" -> Kind.MIDI
            "pad" -> Kind.PAD
            "rgt" -> Kind.REGIST
            "set" -> Kind.SET
            "instrument" -> Kind.INSTRUMENT
            else -> Kind.OTHER
        }
    }

    /** Lists a folder of a document tree. Folders first, then known file types, both sorted by name. Unknown files are hidden. */
    fun list(cr: ContentResolver, tree: Uri, parentDocId: String): List<LibEntry> {
        val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val out = ArrayList<LibEntry>()
        val c = cr.query(childUri, cols, null, null, null) ?: return out
        c.use {
            while (it.moveToNext()) {
                val id = it.getString(0) ?: continue
                val name = it.getString(1) ?: continue
                val mime = it.getString(2) ?: ""
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) out.add(LibEntry(name, id, true, Kind.FOLDER))
                else {
                    val k = kindOf(name)
                    if (k != Kind.OTHER) out.add(LibEntry(name, id, false, k))
                }
            }
        }
        out.sortWith(compareBy<LibEntry>({ !it.isDir }, { it.name.lowercase() }))
        return out
    }

    fun docUri(tree: Uri, docId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)

    fun displayName(cr: ContentResolver, uri: Uri): String {
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) return it.getString(0) ?: "file"
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    }

    /** Reads a whole file; refuses files over [MidiReader.MAX_BYTES] so a wrong pick cannot exhaust memory. */
    fun readBytes(cr: ContentResolver, uri: Uri): ByteArray {
        val input = cr.openInputStream(uri) ?: throw FormatException("Cannot open the file")
        return input.use { ins ->
            val bos = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                total += n
                if (total > MidiReader.MAX_BYTES) throw FormatException("File too large (over 64 MB)")
                bos.write(buf, 0, n)
            }
            bos.toByteArray()
        }
    }
}
