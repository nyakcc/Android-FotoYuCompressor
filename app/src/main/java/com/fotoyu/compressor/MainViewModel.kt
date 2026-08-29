package com.fotoyu.compressor

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.roundToInt

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val historyPrefs = application.getSharedPreferences("history", Context.MODE_PRIVATE)

    private val _maxWidth = MutableStateFlow(prefs.getInt("maxWidth", 1440))
    val maxWidth: StateFlow<Int> = _maxWidth

    private val _splitCount = MutableStateFlow(prefs.getInt("splitCount", 2000))
    val splitCount: StateFlow<Int> = _splitCount

    private val _sourceUri = MutableStateFlow<Uri?>(null)
    val sourceUri: StateFlow<Uri?> = _sourceUri

    private val _outputUri = MutableStateFlow<Uri?>(null)
    val outputUri: StateFlow<Uri?> = _outputUri

    private val _photos = MutableStateFlow<List<PhotoItem>>(emptyList())
    val photos: StateFlow<List<PhotoItem>> = _photos

    private val _totalSize = MutableStateFlow(0L)
    val totalSize: StateFlow<Long> = _totalSize

    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val history: StateFlow<List<HistoryItem>> = _history

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress

    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText

    private val _timeRemaining = MutableStateFlow("--:--")
    val timeRemaining: StateFlow<String> = _timeRemaining

    private val _quality = MutableStateFlow(85)
    val quality: StateFlow<Int> = _quality

    private var processingJob: Job? = null

    init {
        loadHistory()
    }

    fun updateMaxWidth(value: Int) {
        _maxWidth.value = value
        prefs.edit().putInt("maxWidth", value).apply()
    }

    fun updateSplitCount(value: Int) {
        _splitCount.value = value
        prefs.edit().putInt("splitCount", value).apply()
    }

    fun setSourceFolder(uri: Uri) {
        _sourceUri.value = uri
        _photos.value = emptyList()
        _totalSize.value = 0L
        scanFolders()
    }

    fun setOutputFolder(uri: Uri) {
        _outputUri.value = uri
    }

    private fun scanFolders() {
        val root = _sourceUri.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var currentSize = 0L
                val found = recursivePhotos(root) { size ->
                    currentSize += size
                }
                _photos.value = found
                _totalSize.value = currentSize
            } catch (e: Exception) {
                _statusText.value = "Scan error"
            }
        }
    }

    private fun recursivePhotos(root: Uri, onFileFound: (Long) -> Unit): List<PhotoItem> {
        val result = mutableListOf<PhotoItem>()
        val queue = ArrayDeque<Pair<DocumentFile, String>>()
        DocumentFile.fromTreeUri(getApplication(), root)?.let { queue.add(it to "") } ?: return result
        
        val extensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "tif", "tiff", "gif")

        while (queue.isNotEmpty()) {
            val (folder, rel) = queue.removeFirst()
            folder.listFiles().forEach { f ->
                val name = f.name ?: return@forEach
                val ext = name.substringAfterLast('.', "").lowercase()
                if (f.isDirectory) {
                    queue.add(f to if (rel.isEmpty()) name else "$rel/$name")
                } else if (f.isFile && extensions.contains(ext)) {
                    val size = f.length()
                    onFileFound(size)
                    result.add(PhotoItem(f.uri, name, if (rel.isEmpty()) name else "$rel/$name", size))
                }
            }
        }
        return result
    }

    fun startProcessing() {
        if (_isProcessing.value) return
        val outRoot = _outputUri.value ?: return
        val items = _photos.value
        if (items.isEmpty()) return

        _isProcessing.value = true
        _progress.value = 0
        
        val maxW = _maxWidth.value
        val split = _splitCount.value
        val startTime = System.currentTimeMillis()

        processingJob = viewModelScope.launch(Dispatchers.IO) {
            var success = 0
            try {
                val folderCount = (items.size + split - 1) / split
                val parent = DocumentFile.fromTreeUri(getApplication(), outRoot) ?: throw IOException()
                
                val folders = (1..folderCount).map { n ->
                    val name = "Folder_${n.toString().padStart(3, '0')}"
                    parent.findFile(name) ?: parent.createDirectory(name) ?: throw IOException()
                }

                for ((index, item) in items.withIndex()) {
                    if (!isActive) break
                    val folder = folders[index / split]
                    try {
                        val base = item.name.substringBeforeLast('.', item.name)
                        val dest = folder.createFile("image/jpeg", "$base.jpg") ?: throw IOException()
                        compressToUri(item.uri, dest.uri, maxW)
                        success++
                    } catch (e: Exception) {}

                    val percent = ((index + 1).toFloat() / items.size * 100).toInt()
                    val elapsed = System.currentTimeMillis() - startTime
                    val remainingMs = if (index > 0) (elapsed / (index + 1)) * (items.size - (index + 1)) else 0L
                    
                    _progress.value = percent
                    _statusText.value = "Compressing: ${item.name}"
                    _timeRemaining.value = formatTime(remainingMs)
                }
                
                saveHistoryItem(success, folderCount, _totalSize.value, isActive)
                
            } catch (e: Exception) {
                _statusText.value = "Error"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun stopProcessing() {
        processingJob?.cancel()
    }

    private fun compressToUri(src: Uri, dest: Uri, maxWidth: Int) {
        val resolver = getApplication<Application>().contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(src)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: throw IOException()
        
        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > maxWidth * 2) sample *= 2
        
        val opts = BitmapFactory.Options().apply { 
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888 
        }
        val bitmap = resolver.openInputStream(src)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: throw IOException()
        
        var scaled = bitmap
        val maxSide = max(bitmap.width, bitmap.height)
        if (maxSide > maxWidth) {
            val ratio = maxWidth.toFloat() / maxSide.toFloat()
            scaled = bitmap.scale((bitmap.width * ratio).roundToInt(), (bitmap.height * ratio).roundToInt(), true)
            if (scaled !== bitmap) bitmap.recycle()
        }

        val rotated = try {
            resolver.openInputStream(src)?.use { input ->
                val exif = ExifInterface(input)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> rotate(scaled, 90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> rotate(scaled, 180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> rotate(scaled, 270f)
                    else -> scaled
                }
            } ?: scaled
        } catch (e: Exception) { scaled }
        
        if (rotated !== scaled) scaled.recycle()

        val bos = ByteArrayOutputStream()
        rotated.compress(Bitmap.CompressFormat.JPEG, _quality.value, bos)
        val bytes = bos.toByteArray()
        rotated.recycle()
        resolver.openOutputStream(dest)?.use { it.write(bytes) } ?: throw IOException()
    }

    private fun rotate(src: Bitmap, deg: Float): Bitmap {
        val m = android.graphics.Matrix(); m.postRotate(deg)
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    private fun formatTime(ms: Long): String {
        val s = ms / 1000
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
    }

    fun loadHistory() {
        val json = historyPrefs.getString("items", "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<HistoryItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(HistoryItem(
                o.getString("date"), o.getString("path"), o.getInt("count"),
                o.getString("orig"), o.getString("res"), o.getInt("save"), o.getBoolean("ok")
            ))
        }
        _history.value = list
    }

    private fun saveHistoryItem(count: Int, folders: Int, size: Long, ok: Boolean) {
        val item = JSONObject().apply {
            put("date", SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id")).format(Date()))
            put("path", _sourceUri.value?.path?.substringAfterLast(':') ?: "Unknown")
            put("count", count)
            put("orig", formatSize(size))
            put("res", formatSize((size * 0.3).toLong()))
            put("save", if (ok) 70 else 0)
            put("ok", ok)
        }
        val arr = JSONArray(historyPrefs.getString("items", "[]"))
        val newArr = JSONArray().apply { put(item) }
        for (i in 0 until arr.length()) if (i < 49) newArr.put(arr.get(i))
        historyPrefs.edit().putString("items", newArr.toString()).apply()
        loadHistory()
    }

    private fun formatSize(b: Long): String {
        val mb = b / (1024 * 1024.0)
        return if (mb > 1024) String.format(Locale.US, "%.1f GB", mb / 1024) else String.format(Locale.US, "%.1f MB", mb)
    }
}
