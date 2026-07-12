package com.yura.app.library

import java.io.File

object LocalBookFileCleaner {
    fun deleteOwnedFiles(vararg files: File?): Boolean =
        files.filterNotNull().fold(true) { success, file ->
            (!file.exists() || file.delete()) && success
        }
}
