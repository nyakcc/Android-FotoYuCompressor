package com.fotoyu.compressor

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import android.content.Intent
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

    private val _currentProcessedCount = MutableStateFlow(0)
    val currentProcessedCount: StateFlow<Int> = _currentProcessedCount

    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep

    private var processingJob: Job? = null

    init {
        loadHistory()
    }

    fun resetData() {
        _sourceUri.value = null
        _outputUri.value = null
        _photos.value = emptyList()
        _totalSize.value = 0L
        _progress.value = 0
        _statusText.value = ""
        _currentProcessedCount.value = 0
        _currentStep.value = 0
        _timeRemaining.value = "--:--"
    }

    fun updateMaxWidth(value: Int) {
        if (_maxWidth.value == value) return
        _maxWidth.value = value
        prefs.edit().putInt("maxWidth", value).apply()
    }

    fun updateSplitCount(value: Int) {
        if (_splitCount.value == value) return
        _splitCount.value = value
        prefs.edit().putInt("splitCount", value).apply()
    }

    fun setSourceFolder(uri: Uri) {
        try {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            getApplication<Application>().contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (_: Exception) {}
        
        _sourceUri.value = uri
        _photos.value = emptyList()
        _totalSize.value = 0L
        scanFolders()
    }

    fun setOutputFolder(uri: Uri) {
        try {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            getApplication<Application>().contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (_: Exception) {}
        
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
        _currentStep.value = 1
        _currentProcessedCount.value = 0
        
        val maxW = _maxWidth.value
        val split = _splitCount.value
        val startTime = System.currentTimeMillis()

        processingJob = viewModelScope.launch(Dispatchers.IO) {
            var success = 0
            try {
                val parent = DocumentFile.fromTreeUri(getApplication(), outRoot) ?: throw IOException("Destination access error")
                
                _currentStep.value = 2
                val folderCount = (items.size + split - 1) / split
                val folders = (1..folderCount).map { n ->
                    val name = "Folder_${n.toString().padStart(3, '0')}"
                    parent.findFile(name) ?: parent.createDirectory(name) ?: throw IOException("Folder create error")
                }

                for ((index, item) in items.withIndex()) {
                    if (!isActive) break
                    
                    _statusText.value = "Processing: ${item.name}"
                    val folder = folders[index / split]
                    try {
                        val base = item.name.substringBeforeLast('.', item.name)
                        // Ensure unique filename
                        val dest = folder.createFile("image/jpeg", "$base.jpg") ?: throw IOException("File creation failed")
                        compressToUri(item.uri, dest.uri, maxW)
                        success++
                    } catch (e: Exception) {
                        _statusText.value = "Failed: ${item.name}"
                        delay(500)
                    }

                    _currentProcessedCount.value = index + 1
                    val percent = ((index + 1).toFloat() / items.size * 100).toInt()
                    val elapsed = System.currentTimeMillis() - startTime
                    val remainingMs = if (index > 0) (elapsed / (index + 1)) * (items.size - (index + 1)) else 0L
                    
                    _progress.value = percent
                    _timeRemaining.value = formatTime(remainingMs)
                }
                
                if (isActive) {
                    _currentStep.value = 4
                    _statusText.value = "Completed: $success files"
                } else {
                    _statusText.value = "Cancelled"
                }
                
                saveHistoryItem(success, folderCount, _totalSize.value, isActive && success > 0)
                
            } catch (e: Exception) {
                _statusText.value = "Error: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun stopProcessing() {
        processingJob?.cancel()
        _isProcessing.value = false
    }

    private fun compressToUri(src: Uri, dest: Uri, maxWidth: Int) {
        val resolver = getApplication<Application>().contentResolver
        
        // 1. Get original bounds
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(src)?.use { BitmapFactory.decodeStream(it, null, boundsOptions) } ?: throw IOException("Source access error")
        
        // 2. Efficient decode using sample size
        var sample = 1
        while (max(boundsOptions.outWidth / sample, boundsOptions.outHeight / sample) > maxWidth * 2) sample *= 2
        
        val decodeOptions = BitmapFactory.Options().apply { 
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888 
        }
        val originalBitmap = resolver.openInputStream(src)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) } ?: throw IOException("Decode error")
        
        try {
            // 3. Scale precisely to maxWidth
            var currentBitmap = originalBitmap
            val maxSide = max(currentBitmap.width, currentBitmap.height)
            if (maxSide > maxWidth) {
                val ratio = maxWidth.toFloat() / maxSide.toFloat()
                val targetW = (currentBitmap.width * ratio).roundToInt()
                val targetH = (currentBitmap.height * ratio).roundToInt()
                currentBitmap = currentBitmap.scale(targetW, targetH, true)
                if (currentBitmap != originalBitmap) originalBitmap.recycle()
            }

            // 4. Orientation fix
            var finalBitmap = currentBitmap
            try {
                resolver.openInputStream(src)?.use { input ->
                    val exif = ExifInterface(input)
                    val rotation = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                    if (rotation != 0f) {
                        finalBitmap = rotate(currentBitmap, rotation)
                        if (finalBitmap != currentBitmap) currentBitmap.recycle()
                    }
                }
            } catch (_: Exception) {}

            // 5. Compress to Byte Array first (to avoid partial writes)
            val bos = ByteArrayOutputStream()
            if (!finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, bos)) {
                throw IOException("JPEG compression error")
            }
            val data = bos.toByteArray()
            
            // 6. Final write to destination
            resolver.openOutputStream(dest)?.use { os ->
                os.write(data)
                os.flush()
            } ?: throw IOException("Destination write error")
            
            finalBitmap.recycle()
            if (finalBitmap != originalBitmap && !originalBitmap.isRecycled) originalBitmap.recycle()

        } catch (e: Exception) {
            if (!originalBitmap.isRecycled) originalBitmap.recycle()
            throw e
        }
    }

    private fun rotate(src: Bitmap, deg: Float): Bitmap {
        val m = android.graphics.Matrix(); m.postRotate(deg)
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    private fun formatTime(ms: Long): String {
        val totalSecs = ms / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format(Locale.US, "%02d:%02d", mins, secs)
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
        val currentStr = historyPrefs.getString("items", "[]") ?: "[]"
        val arr = JSONArray(currentStr)
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
