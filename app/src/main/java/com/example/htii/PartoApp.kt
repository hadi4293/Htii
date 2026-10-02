package com.example.htii

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private val Night = Color(0xFF080D19)
private val Indigo = Color(0xFF818CF8)
private val Aqua = Color(0xFF5EEAD4)
private val SoftWhite = Color(0xFFF1F4FF)
private val MutedWhite = Color(0xFFA9B2CB)

@Composable
fun PartoApp(viewModel: StreamViewModel) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions.values.any { it } ||
            viewModel.state.value.hasVideoPermission
        viewModel.setVideoPermission(granted)
        if (!granted) viewModel.setMessage(context.getString(R.string.permission_denied))
    }
    val documentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(viewModel::openDocument)
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessage()
    }

    val requestVideoAccess = {
        val permissions = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissions)
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF141A32),
                            Night,
                            Color(0xFF101C2A),
                            Color(0xFF0A101E),
                        ),
                    ),
                ),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 60.dp, start = 18.dp)
                    .size(190.dp)
                    .blur(80.dp)
                    .background(Indigo.copy(alpha = 0.23f), CircleShape),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .absoluteOffset(x = 80.dp)
                    .size(170.dp)
                    .blur(75.dp)
                    .background(Aqua.copy(alpha = 0.14f), CircleShape),
            )

            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    PartoNavigationBar(
                        selectedTab = state.selectedTab,
                        onTabSelected = viewModel::selectTab,
                    )
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    AppHeader(
                        state = state,
                    )
                    when (state.selectedTab) {
                        AppTab.LIBRARY -> LibraryScreen(
                            state = state,
                            onSearch = viewModel::setSearchQuery,
                            onPermission = requestVideoAccess,
                            onPickVideo = { documentLauncher.launch(arrayOf("video/*")) },
                            onPlay = viewModel::playLocalVideo,
                            onBrowse = { viewModel.selectTab(AppTab.RECEIVER) },
                        )
                        AppTab.RECEIVER -> ReceiverScreen(
                            state = state,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppHeader(state: StreamUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(
                    Brush.linearGradient(listOf(Indigo, Color(0xFF6264D9), Aqua.copy(alpha = .8f))),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Tv, contentDescription = null, tint = Color.White, modifier = Modifier.size(25.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                color = SoftWhite,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = state.selectedVideoTitle ?: stringResource(R.string.app_tagline),
                color = MutedWhite,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun PartoNavigationBar(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
) {
    val items = listOf(
        Triple(AppTab.LIBRARY, R.string.tab_library, Icons.Outlined.VideoLibrary),
        Triple(AppTab.RECEIVER, R.string.tab_receiver, Icons.Outlined.Devices),
    )
    NavigationBar(
        containerColor = Color(0xE9121727),
        tonalElevation = 0.dp,
    ) {
        items.forEach { (tab, label, icon) ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(stringResource(label), fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = SoftWhite,
                    indicatorColor = Indigo.copy(alpha = .38f),
                    unselectedIconColor = MutedWhite,
                    unselectedTextColor = MutedWhite,
                ),
            )
        }
    }
}

@Composable
private fun LibraryScreen(
    state: StreamUiState,
    onSearch: (String) -> Unit,
    onPermission: () -> Unit,
    onPickVideo: () -> Unit,
    onPlay: (VideoItem) -> Unit,
    onBrowse: () -> Unit,
) {
    val filteredVideos = remember(state.videos, state.searchQuery) {
        if (state.searchQuery.isBlank()) state.videos
        else state.videos.filter { it.title.contains(state.searchQuery, ignoreCase = true) }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = 4.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            HeroCard(onBrowse = onBrowse)
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        stringResource(R.string.your_videos),
                        color = SoftWhite,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.video_count, state.videos.size),
                        color = MutedWhite,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                TextButton(onClick = onPickVideo) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = Aqua)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.pick_video), color = Aqua)
                }
            }
        }
        if (state.hasVideoPermission) {
            item {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearch,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_videos), color = MutedWhite) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null, tint = MutedWhite) },
                    shape = RoundedCornerShape(18.dp),
                    colors = glassTextFieldColors(),
                )
            }
            when {
                state.isLoadingVideos -> item {
                    Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Aqua)
                    }
                }
                filteredVideos.isEmpty() -> item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Movie, null, tint = Aqua, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.empty_library), color = MutedWhite)
                    }
                }
                else -> items(filteredVideos, key = { it.uri.toString() }) { video ->
                    VideoRow(video = video, onPlay = { onPlay(video) })
                }
            }
        } else {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.permission_required),
                        color = SoftWhite,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    AccentButton(
                        label = stringResource(R.string.allow_access),
                        icon = Icons.Outlined.VideoLibrary,
                        onClick = onPermission,
                    )
                }
            }
        }
        item {
            Text(
                stringResource(R.string.supported_notice),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                color = MutedWhite,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun HeroCard(onBrowse: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        background = Brush.linearGradient(
            listOf(
                Color(0xFF434D9A).copy(alpha = .68f),
                Color(0xFF222943).copy(alpha = .62f),
                Color(0xFF173C47).copy(alpha = .6f),
            ),
        ),
        onClick = onBrowse,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.hero_title),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 34.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.hero_subtitle),
                    color = Color.White.copy(alpha = .8f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Devices, null, tint = Aqua, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        stringResource(R.string.receiver_tab),
                        color = Aqua,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .size(82.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = .1f))
                    .border(1.dp, Color.White.copy(alpha = .2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Tv, null, tint = Color.White, modifier = Modifier.size(38.dp))
            }
        }
    }
}

@Composable
private fun VideoRow(video: VideoItem, onPlay: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onPlay,
        padding = 13.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        Brush.linearGradient(listOf(Indigo.copy(.55f), Aqua.copy(.24f))),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Movie, null, tint = Color.White, modifier = Modifier.size(25.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    video.title,
                    color = SoftWhite,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    formatVideoInfo(video),
                    color = MutedWhite,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            IconButton(onClick = onPlay) {
                Icon(Icons.Outlined.PlayArrow, stringResource(R.string.send_video), tint = Aqua)
            }
        }
    }
}

@Composable
private fun ReceiverScreen(state: StreamUiState) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 18.dp,
            vertical = 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    stringResource(R.string.receiver_title),
                    color = SoftWhite,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    stringResource(R.string.receiver_instructions),
                    color = MutedWhite,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    state.selectedVideoTitle?.let {
                        stringResource(R.string.selected_video, it)
                    } ?: stringResource(R.string.no_video_selected),
                    color = SoftWhite,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                if (state.serverUrl.isBlank()) {
                    Text(stringResource(R.string.choose_video_first), color = MutedWhite)
                } else {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        SelectionContainer {
                            Text(
                                state.serverUrl,
                                color = Aqua,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    TextButton(
                        onClick = {
                            context.getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText("Parto receiver URL", state.serverUrl))
                        },
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = Aqua)
                        Spacer(Modifier.width(7.dp))
                        Text(stringResource(R.string.copy_address), color = Aqua)
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.http_network_warning),
                color = MutedWhite,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
@SuppressLint("SetJavaScriptEnabled")
private fun BrowserScreen(
    state: StreamUiState,
    onPageChanged: (String) -> Unit,
    onDetected: (List<WebVideo>) -> Unit,
    onDirectVideo: (String, String) -> Unit,
    onPlayVideo: (WebVideo) -> Unit,
    onError: (String) -> Unit,
) {
    var address by rememberSaveable { mutableStateOf(state.browserUrl) }
    var pageUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var browserLoading by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, webView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webView?.onPause()
                Lifecycle.Event.ON_RESUME -> webView?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val openAddress: () -> Unit = {
        val url = VideoTypes.validWebUrl(address)
        if (url == null) {
            onError(context.getString(R.string.link_error))
        } else {
            keyboard?.hide()
            focusManager.clearFocus()
            address = url
            onPageChanged(url)
            val mimeType = VideoTypes.fromUrl(url)
            if (mimeType != null) {
                pageUrl = null
                onDirectVideo(
                    url,
                    url.substringAfterLast('/').substringBefore('?')
                        .ifBlank { context.getString(R.string.direct_video) },
                )
            } else {
                pageUrl = url
                onDetected(emptyList())
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 18.dp, vertical = 4.dp),
    ) {
        Text(
            stringResource(R.string.browser_title),
            color = SoftWhite,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            stringResource(R.string.browser_subtitle),
            color = MutedWhite,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.url_hint), color = MutedWhite) },
                    leadingIcon = { Icon(Icons.Outlined.Language, null, tint = Aqua) },
                    shape = RoundedCornerShape(17.dp),
                    colors = glassTextFieldColors(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { openAddress() }),
                )
            }
            IconButton(
                onClick = openAddress,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Indigo),
            ) {
                Icon(Icons.Outlined.PlayArrow, stringResource(R.string.open_url), tint = Color.White)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 9.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            TextButton(
                onClick = {
                    webView?.evaluateJavascript(VIDEO_SCAN_SCRIPT) { result ->
                        val videos = parseWebVideos(result)
                        onDetected(videos)
                        if (videos.isEmpty()) {
                            onError(context.getString(R.string.unsupported_page))
                        }
                    }
                },
                enabled = pageUrl != null && !browserLoading,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.Search, null, tint = Aqua)
                Spacer(Modifier.width(7.dp))
                Text(stringResource(R.string.find_video), color = Aqua)
            }
            Text(
                text = if (browserLoading) stringResource(R.string.browser_loading)
                else state.detectedWebVideos.size.takeIf { it > 0 }
                    ?.let { "ویدیو پیدا شد: $it" }
                    ?: stringResource(R.string.found_videos),
                modifier = Modifier.align(Alignment.CenterVertically),
                color = MutedWhite,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        if (state.detectedWebVideos.isNotEmpty()) {
            state.detectedWebVideos.forEach { video ->
                WebVideoRow(video = video, onClick = { onPlayVideo(video) })
            }
            Spacer(Modifier.height(8.dp))
        }

        val currentPage = pageUrl
        if (currentPage == null) {
            BrowserWelcome(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        } else {
            AndroidView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(24.dp)),
                factory = { context ->
                    WebView(context).apply {
                        webView = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.setSupportMultipleWindows(false)
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.mediaPlaybackRequiresUserGesture = true
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            settings.safeBrowsingEnabled = true
                        }
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                val blocked = request.url.scheme !in setOf("http", "https")
                                if (request.isForMainFrame && blocked) {
                                    onError(context.getString(R.string.webview_blocked))
                                }
                                return request.isForMainFrame && blocked
                            }

                            override fun onPageStarted(
                                view: WebView,
                                url: String?,
                                favicon: android.graphics.Bitmap?,
                            ) {
                                browserLoading = true
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                browserLoading = false
                                view.evaluateJavascript(VIDEO_SCAN_SCRIPT) { result ->
                                    onDetected(parseWebVideos(result))
                                }
                                view.postDelayed(
                                    {
                                        if (view.url == url) {
                                            view.evaluateJavascript(VIDEO_SCAN_SCRIPT) { result ->
                                                onDetected(parseWebVideos(result))
                                            }
                                        }
                                    },
                                    1_500,
                                )
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError,
                            ) {
                                if (request.isForMainFrame) {
                                    browserLoading = false
                                    onError("صفحه باز نشد: ${error.description}")
                                }
                            }
                        }
                        loadUrl(currentPage)
                    }
                },
                update = { view ->
                    if (view.url != currentPage) view.loadUrl(currentPage)
                },
                onRelease = { view ->
                    view.stopLoading()
                    view.destroy()
                    if (webView === view) webView = null
                },
            )
        }

        Text(
            stringResource(R.string.supported_notice),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 7.dp),
            color = MutedWhite,
            style = MaterialTheme.typography.labelSmall,
        )
    }

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }
}

@Composable
private fun BrowserWelcome(modifier: Modifier = Modifier) {
    GlassCard(
        modifier = modifier,
        background = Brush.linearGradient(
            listOf(Color(0xFF252F54).copy(alpha = .56f), Color(0xFF112B36).copy(alpha = .54f)),
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Language, null, tint = Aqua, modifier = Modifier.size(46.dp))
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.browser_subtitle), color = SoftWhite)
                Spacer(Modifier.height(7.dp))
                Text(stringResource(R.string.no_web_video), color = MutedWhite)
            }
        }
    }
}

@Composable
private fun WebVideoRow(video: WebVideo, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        padding = 11.dp,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Movie, null, tint = Aqua)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    video.title,
                    color = SoftWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    video.mimeType,
                    color = MutedWhite,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Icon(Icons.Outlined.PlayArrow, stringResource(R.string.send_video), tint = Aqua)
        }
    }
}

private fun parseWebVideos(result: String?): List<WebVideo> {
    if (result.isNullOrBlank() || result == "null") return emptyList()
    return runCatching {
        val jsonString = JSONArray("[$result]").optString(0)
        val json = JSONObject(jsonString)
        val pageTitle = json.optString("title").ifBlank { "ویدیوی وب" }
        val videos = json.optJSONArray("videos") ?: return emptyList()
        buildList {
            for (index in 0 until videos.length()) {
                val item = videos.optJSONObject(index) ?: continue
                val url = VideoTypes.validWebUrl(item.optString("url")) ?: continue
                val type = item.optString("type").takeIf { it.startsWith("video/") }
                    ?: VideoTypes.fromUrl(url)
                    ?: continue
                add(
                    WebVideo(
                        title = item.optString("title").ifBlank { pageTitle },
                        url = url,
                        mimeType = type,
                    ),
                )
            }
        }.distinctBy { it.url }
    }.getOrDefault(emptyList())
}

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    padding: androidx.compose.ui.unit.Dp = 18.dp,
    background: Brush = Brush.linearGradient(
        listOf(Color.White.copy(alpha = .105f), Color.White.copy(alpha = .045f)),
    ),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(25.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(background)
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = .23f), Color.White.copy(alpha = .065f)),
                    ),
                ),
                shape,
            )
            .padding(padding),
        content = content,
    )
}

@Composable
private fun AccentButton(
    label: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = !loading,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Indigo,
            contentColor = Color.White,
            disabledContainerColor = Indigo.copy(alpha = .65f),
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(9.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(8.dp))
        }
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun glassTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = SoftWhite,
    unfocusedTextColor = SoftWhite,
    focusedBorderColor = Aqua.copy(alpha = .7f),
    unfocusedBorderColor = Color.White.copy(alpha = .17f),
    cursorColor = Aqua,
    focusedContainerColor = Color.White.copy(alpha = .045f),
    unfocusedContainerColor = Color.White.copy(alpha = .045f),
)

private fun formatVideoInfo(video: VideoItem): String {
    val duration = if (video.durationMs > 0) {
        val totalSeconds = video.durationMs / 1_000
        "%d:%02d".format(Locale.ROOT, totalSeconds / 60, totalSeconds % 60)
    } else {
        ""
    }
    val size = if (video.sizeBytes > 0) {
        val mebibytes = video.sizeBytes / (1024.0 * 1024.0)
        if (mebibytes >= 1_000) "%.1f GB".format(Locale.ROOT, mebibytes / 1_024)
        else "%.0f MB".format(Locale.ROOT, mebibytes)
    } else {
        ""
    }
    return listOf(duration, size, video.mimeType.substringAfter('/')).filter(String::isNotBlank)
        .joinToString("  ·  ")
}

private const val VIDEO_SCAN_SCRIPT = """
    (function() {
      const items = [];
      document.querySelectorAll('video').forEach(video => {
        const title = video.getAttribute('aria-label') || video.getAttribute('title') || document.title;
        if (video.currentSrc) items.push({url: video.currentSrc, type: video.getAttribute('type') || '', title});
        if (video.src) items.push({url: video.src, type: video.getAttribute('type') || '', title});
        video.querySelectorAll('source').forEach(source => {
          if (source.src) items.push({url: source.src, type: source.type || '', title});
        });
      });
      return JSON.stringify({title: document.title, videos: items});
    })();
"""
