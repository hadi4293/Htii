package com.example.htii

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.example.htii.ui.theme.HtiiTheme

class MainActivity : ComponentActivity() {
    private val viewModel: StreamViewModel by viewModels()
    private var sessionManager: SessionManager? = null

    private val castSessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) = Unit

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            viewModel.setCastSession(session)
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            viewModel.setCastSession(null)
            viewModel.setMessage("اتصال Google Cast برقرار نشد (کد $error).")
        }

        override fun onSessionEnding(session: CastSession) = Unit

        override fun onSessionEnded(session: CastSession, error: Int) {
            viewModel.setCastSession(null)
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            viewModel.setCastSession(session)
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            viewModel.setCastSession(null)
            viewModel.setMessage("اتصال Google Cast بازیابی نشد (کد $error).")
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            viewModel.setCastSession(null)
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        viewModel.refreshPermission()

        try {
            val castContext = CastContext.getSharedInstance(this)
            sessionManager = castContext.sessionManager.apply {
                addSessionManagerListener(castSessionListener, CastSession::class.java)
            }
            viewModel.setCastAvailable(true)
            viewModel.setCastSession(castContext.sessionManager.currentCastSession)
        } catch (exception: Exception) {
            viewModel.setCastAvailable(false)
            viewModel.setMessage(
                "Google Cast روی این دستگاه آماده نیست؛ امکان استفاده از DLNA همچنان برقرار است.",
            )
        }

        setContent {
            HtiiTheme {
                PartoApp(
                    viewModel = viewModel,
                    onCastSetupError = {
                        viewModel.setMessage("Google Cast آماده نیست؛ از تلویزیون‌های DLNA استفاده کن.")
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermission()
    }

    override fun onDestroy() {
        sessionManager?.removeSessionManagerListener(castSessionListener, CastSession::class.java)
        super.onDestroy()
    }
}
