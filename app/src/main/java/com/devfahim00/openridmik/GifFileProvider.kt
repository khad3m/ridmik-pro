package com.devfahim00.openridmik

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Lightweight read-only ContentProvider for sharing cached GIF files with target apps
 * via InputConnection.commitContent() without requiring AndroidX FileProvider.
 */
class GifFileProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "image/gif"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val ctx = context ?: throw FileNotFoundException("No context")
        val name = uri.lastPathSegment ?: throw FileNotFoundException("Missing file name")
        // Strict filename sanitization to prevent path traversal
        if (!name.matches(Regex("[a-zA-Z0-9_.-]+\\.gif"))) {
            throw FileNotFoundException("Invalid gif name")
        }
        val file = File(File(ctx.cacheDir, "gifs"), name)
        if (!file.exists()) throw FileNotFoundException("Gif not cached: $name")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val ctx = context ?: return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
        val name = uri.lastPathSegment ?: "sticker.gif"
        val file = File(File(ctx.cacheDir, "gifs"), name)
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(cols)
        val row = Array<Any?>(cols.size) { i ->
            when (cols[i]) {
                OpenableColumns.DISPLAY_NAME -> name
                OpenableColumns.SIZE -> if (file.exists()) file.length() else 0L
                else -> null
            }
        }
        cursor.addRow(row)
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
