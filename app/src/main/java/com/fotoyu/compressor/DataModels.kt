package com.fotoyu.compressor

import android.net.Uri

data class PhotoItem(
    val uri: Uri,
    val name: String,
    val relativePath: String,
    val size: Long
)

data class HistoryItem(
    val date: String,
    val sourcePath: String,
    val photoCount: Int,
    val originalSize: String,
    val resultSize: String,
    val savingPercent: Int,
    val isSuccess: Boolean,
    val statusMsg: String? = null
)
