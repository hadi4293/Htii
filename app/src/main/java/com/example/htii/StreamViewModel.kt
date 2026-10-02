package com.example.htii

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StreamViewModel(application: Application) : AndroidViewModel(application) {
    private val mediaLibrary = MediaLibrary(application)
    private val dlnaClient = DlnaClient(application)
    private val mediaServer = LocalMediaServer(application.contentResolver)
    private val mutableState = MutableStateFlow(StreamUiState())
    val state: StateFlow<StreamUiState> = mutableState.asStateFlow()
    private var castSession: CastSession? = null

    init {
        LocalMediaServerRegistry.register(mediaServer)
    }

    fun setVideoPermission(granted: Boolean) {
        val application = getApplication<Application>()
        mutableState.value = mutableState.value.copy(
            hasVideoPermission = granted,
            message = if (granted) null else application.getString(R.string.permission_denied),
        )
        if (granted) loadVideos()
    }

    fun refreshPermission() {
        val application = getApplication<Application>()
        val permissions = if (Build.VERSION.SDK_INT >= 34) {
            listOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
        } else if (Build.VERSION.SDK_INT >= 33) {
            listOf(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val granted = permissions.any {
            ContextCompat.checkSelfPermission(application, it) == PackageManager.PERMISSION_GRANTED
        }
        if (granted != mutableState.value.hasVideoPermission) setVideoPermission(granted)
    }

    fun loadVideos() {
        if (!mutableState.value.hasVideoPermission) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isLoadingVideos = true)
            try {
                val videos = withContext(Dispatchers.IO) { mediaLibrary.loadVideos() }
                mutableState.value = mutableState.value.copy(videos = videos, isLoadingVideos = false)
            } catch (exception: Exception) {
                mutableState.value = mutableState.value.copy(
                    isLoadingVideos = false,
                    message = "ویدیوها بارگذاری نشدند: ${exception.localizedMessage ?: "خطای ناشناخته"}",
                )
            }
        }
    }

    fun selectTab(tab: AppTab) {
        mutableState.value = mutableState.value.copy(selectedTab = tab)
    }

    fun setSearchQuery(query: String) {
        mutableState.value = mutableState.value.copy(searchQuery = query)
    }

    fun scanDevices() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isScanning = true)
            try {
                val devices = dlnaClient.discover()
                val previous = mutableState.value.selectedDlnaDevice
                val selected = devices.firstOrNull { it.id == previous?.id }
                mutableState.value = mutableState.value.copy(
                    dlnaDevices = devices,
                    selectedDlnaDevice = selected,
                    isScanning = false,
                )
            } catch (exception: Exception) {
                mutableState.value = mutableState.value.copy(
                    isScanning = false,
                    message = "جست‌وجوی تلویزیون ناموفق بود: ${exception.localizedMessage ?: "خطای شبکه"}",
                )
            }
        }
    }

    fun selectDlnaDevice(device: DlnaDevice) {
        val message = getApplication<Application>().getString(
            R.string.device_connected,
            device.name,
        )
        mutableState.value = mutableState.value.copy(
            selectedDlnaDevice = device,
            message = message,
        )
    }

    fun setCastSession(session: CastSession?) {
        castSession = session
        mutableState.value = mutableState.value.copy(isCastConnected = session != null)
    }

    fun setCastAvailable(available: Boolean) {
        mutableState.value = mutableState.value.copy(isCastAvailable = available)
    }

    fun setMessage(message: String) {
        mutableState.value = mutableState.value.copy(message = message)
    }

    fun setBrowserUrl(url: String) {
        mutableState.value = mutableState.value.copy(browserUrl = url)
    }

    fun setDetectedWebVideos(videos: List<WebVideo>) {
        mutableState.value = mutableState.value.copy(detectedWebVideos = videos.distinctBy { it.url })
    }

    fun playLocalVideo(video: VideoItem) {
        val session = castSession
        val dlnaDevice = mutableState.value.selectedDlnaDevice
        if (session == null && dlnaDevice == null) {
            mutableState.value = mutableState.value.copy(
                message = getApplication<Application>().getString(R.string.no_dlna_devices),
            )
            return
        }
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isStreaming = true)
            try {
                val url = withContext(Dispatchers.IO) {
                    startLocalStreamService()
                    mediaServer.publish(video)
                }
                if (session != null) {
                    castUrl(session, url, video.title, video.mimeType)
                } else {
                    dlnaClient.play(checkNotNull(dlnaDevice), url, video.title, video.mimeType)
                    showStreamStarted(video.title)
                }
            } catch (exception: Exception) {
                showStreamError(exception)
            } finally {
                mutableState.value = mutableState.value.copy(isStreaming = false)
            }
        }
    }

    fun playWebVideo(video: WebVideo) {
        val session = castSession
        val device = mutableState.value.selectedDlnaDevice
        if (session == null && device == null) {
            mutableState.value = mutableState.value.copy(
                message = getApplication<Application>().getString(R.string.no_dlna_devices),
            )
            return
        }
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isStreaming = true)
            try {
                if (session != null) {
                    castUrl(session, video.url, video.title, video.mimeType)
                } else {
                    dlnaClient.play(checkNotNull(device), video.url, video.title, video.mimeType)
                    showStreamStarted(video.title)
                }
            } catch (exception: Exception) {
                showStreamError(exception)
            } finally {
                mutableState.value = mutableState.value.copy(isStreaming = false)
            }
        }
    }

    fun playDirectLink(url: String, title: String) {
        val validUrl = VideoTypes.validWebUrl(url)
        val mimeType = validUrl?.let(VideoTypes::fromUrl)
        if (validUrl == null || mimeType == null) {
            mutableState.value = mutableState.value.copy(
                message = getApplication<Application>().getString(R.string.link_error),
            )
            return
        }
        playWebVideo(WebVideo(title, validUrl, mimeType))
    }

    fun openDocument(uri: Uri) {
        val application = getApplication<Application>()
        runCatching {
            application.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val details = application.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L
                name to size
            } else {
                null
            }
        }
        val mimeType = application.contentResolver.getType(uri) ?: "video/mp4"
        playLocalVideo(
            VideoItem(
                uri = uri,
                title = details?.first ?: uri.lastPathSegment ?: "Video",
                mimeType = mimeType,
                sizeBytes = details?.second ?: 0L,
            ),
        )
    }

    fun clearMessage() {
        mutableState.value = mutableState.value.copy(message = null)
    }

    override fun onCleared() {
        mediaServer.stop()
        getApplication<Application>().stopService(
            Intent(getApplication<Application>(), StreamingService::class.java),
        )
        super.onCleared()
    }

    private fun startLocalStreamService() {
        val application = getApplication<Application>()
        ContextCompat.startForegroundService(
            application,
            Intent(application, StreamingService::class.java).setAction(StreamingService.ACTION_START),
        )
    }

    private suspend fun castUrl(session: CastSession, url: String, title: String, mimeType: String) {
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
        }
        val mediaInfo = MediaInfo.Builder(url)
            .setContentType(mimeType)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setMetadata(metadata)
            .build()
        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .build()
        val result = session.remoteMediaClient?.load(request)
            ?: throw IllegalStateException("اتصال Google Cast در دسترس نیست.")
        result.setResultCallback { mediaResult ->
            if (!mediaResult.status.isSuccess) {
                mutableState.value = mutableState.value.copy(
                    message = "تلویزیون ویدیو را نپذیرفت: ${mediaResult.status.statusMessage ?: "خطای Cast"}",
                )
            } else {
                mutableState.value = mutableState.value.copy(message = "درخواست پخش ارسال شد: $title")
            }
        }
    }

    private fun showStreamStarted(title: String) {
        mutableState.value = mutableState.value.copy(
            message = getApplication<Application>().getString(R.string.stream_started, title),
        )
    }

    private fun showStreamError(exception: Exception) {
        mutableState.value = mutableState.value.copy(
            message = getApplication<Application>().getString(
                R.string.stream_failed,
                exception.localizedMessage ?: "خطای ناشناخته",
            ),
        )
    }
}
