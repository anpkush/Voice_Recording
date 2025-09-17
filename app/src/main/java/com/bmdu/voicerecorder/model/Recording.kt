package com.bmdu.voicerecorder.model

import java.io.File

data class Recording(
    val file: File,
    val filePath: String,
    val durationMs: Long
)
