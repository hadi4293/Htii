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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StreamViewModel(application: Application) : AndroidViewModel(application) {
    private val mediaLibrary = MediaLibrary(application)
    private val mediaServer = LocalMediaServer(application.contentResolver)
    private val dlnaController = DlnaController(application)
    private val mutableState = MutableStateFlow(StreamUiState())
    val state: StateFlow<StreamUiState> = mutableState.asStateFlow()

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

    fun setVideoUrlInput(url: String) {
        mutableState.value = mutableState.value.copy(videoUrlInput = url)
    }

    fun setMessage(message: String) {
        mutableState.value = mutableState.value.copy(message = message)
    }

    fun discoverDevices() {
        if (mutableState.value.isDiscoveringDevices) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isDiscoveringDevices = true)
            try {
                val devices = withContext(Dispatchers.IO) { dlnaController.discoverDevices() }
                mutableState.value = mutableState.value.copy(
                    isDiscoveringDevices = false,
                    dlnaDevices = devices,
                    selectedDevice = mutableState.value.selectedDevice
                        ?.takeIf { selected -> devices.any { it.id == selected.id } },
                    message = if (devices.isEmpty()) {
                        getApplication<Application>().getString(R.string.no_dlna_devices)
                    } else {
                        null
                    },
                )
            } catch (exception: Exception) {
                mutableState.value = mutableState.value.copy(
                    isDiscoveringDevices = false,
                    message = getApplication<Application>().getString(
                        R.string.discovery_failed,
                        exception.localizedMessage ?: "خطای ناشناخته",
                    ),
                )
            }
        }
    }

    fun selectDevice(device: DlnaDevice) {
        if (mutableState.value.isStreaming) return
        val current = mutableState.value
        mutableState.value = current.copy(selectedDevice = device)
        if (current.playbackUrl.isNotBlank()) {
            playOnDevice(
                device = device,
                url = current.playbackUrl,
                title = current.selectedVideoTitle.orEmpty(),
                mimeType = current.playbackMimeType,
            )
        } else {
            mutableState.value = mutableState.value.copy(
                message = getApplication<Application>().getString(R.string.device_selected, device.name),
            )
        }
    }

    fun playVideoUrl() {
        if (mutableState.value.isStreaming) return
        val application = getApplication<Application>()
        val url = VideoTypes.validWebUrl(mutableState.value.videoUrlInput)
        if (url == null) {
            setMessage(application.getString(R.string.link_error))
            return
        }
        val mimeType = VideoTypes.fromUrl(url)
        if (mimeType == null) {
            setMessage(application.getString(R.string.unsupported_video_link))
            return
        }
        val title = Uri.parse(url).lastPathSegment
            ?.takeIf(String::isNotBlank)
            ?: application.getString(R.string.direct_video)
        val current = mutableState.value
        if (current.serverUrl.isNotBlank()) {
            mediaServer.stop()
            application.stopService(Intent(application, StreamingService::class.java))
        }
        mutableState.value = current.copy(
            selectedTab = AppTab.RECEIVER,
            serverUrl = "",
            playbackUrl = url,
            playbackMimeType = mimeType,
            selectedVideoTitle = title,
        )
        val device = current.selectedDevice
        if (device != null) {
            playOnDevice(device, url, title, mimeType)
        } else {
            mutableState.value = mutableState.value.copy(
                message = application.getString(R.string.select_tv_to_play),
            )
        }
    }

    fun playLocalVideo(video: VideoItem) {
        if (mutableState.value.isStreaming) return
        viewModelScope.launch {
            if (mutableState.value.isStreaming) return@launch
            mutableState.value = mutableState.value.copy(isStreaming = true)
            try {
                startLocalStreamService()
                val url = withContext(Dispatchers.IO) { mediaServer.publish(video) }
                mutableState.value = mutableState.value.copy(
                    isStreaming = false,
                    selectedTab = AppTab.RECEIVER,
                    serverUrl = url,
                    playbackUrl = "${url}video",
                    playbackMimeType = video.mimeType,
                    selectedVideoTitle = video.title,
                )
                val device = mutableState.value.selectedDevice
                if (device != null) {
                    playOnDevice(device, "${url}video", video.title, video.mimeType)
                } else {
                    mutableState.value = mutableState.value.copy(
                        message = getApplication<Application>().getString(
                            R.string.select_tv_to_play,
                        ),
                    )
                }
            } catch (exception: Exception) {
                mediaServer.stop()
                getApplication<Application>().stopService(
                    Intent(getApplication<Application>(), StreamingService::class.java),
                )
                mutableState.value = mutableState.value.copy(
                    isStreaming = false,
                    message = getApplication<Application>().getString(
                        R.string.stream_failed,
                        exception.localizedMessage ?: "خطای ناشناخته",
                    ),
                )
            }
        }
    }

    private fun playOnDevice(
        device: DlnaDevice,
        url: String,
        title: String,
        mimeType: String,
    ) {
        if (mutableState.value.isStreaming) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isStreaming = true)
            try {
                withContext(Dispatchers.IO) {
                    dlnaController.play(device, url, title, mimeType)
                }
                mutableState.value = mutableState.value.copy(
                    isStreaming = false,
                    message = getApplication<Application>().getString(
                        R.string.playing_on_tv,
                        device.name,
                    ),
                )
            } catch (exception: Exception) {
                mutableState.value = mutableState.value.copy(
                    isStreaming = false,
                    message = getApplication<Application>().getString(
                        R.string.tv_play_failed,
                        exception.localizedMessage ?: "خطای ناشناخته",
                    ),
                )
            }
        }
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
}
