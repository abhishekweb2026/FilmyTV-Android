package com.example.ui

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.R
import com.example.ui.theme.FilmyDarkBg
import com.example.ui.theme.FilmyRed
import com.example.util.NetworkUtils
import com.example.webview.FilmyTVWebChromeClient
import com.example.webview.FilmyTVWebViewClient
import kotlinx.coroutines.delay

@Composable
fun FilmyTVScreen(
  websiteUrl: String,
  onShowFileChooser: (
    filePathCallback: ValueCallback<Array<Uri>>?,
    fileChooserParams: WebChromeClient.FileChooserParams?
  ) -> Boolean,
  onExitApp: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val activity = context as? Activity

  val isOnline = remember { NetworkUtils.isNetworkAvailable(context) }
  var webViewInstance by remember { mutableStateOf<WebView?>(null) }
  var isLoading by remember { mutableStateOf(isOnline) }
  var progressValue by remember { mutableFloatStateOf(0f) }
  var hasNetworkError by remember { mutableStateOf(false) }
  var failingUrl by remember { mutableStateOf<String?>(null) }

  // Offline detection safeguard: If offline and no cached page renders, show offline screen promptly
  LaunchedEffect(isOnline) {
    if (!isOnline) {
      delay(1200)
      if (isLoading && webViewInstance?.url == null) {
        hasNetworkError = true
        isLoading = false
      }
    }
  }

  // Fullscreen video state
  var customView by remember { mutableStateOf<View?>(null) }
  var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

  // Helper to exit fullscreen video
  val hideFullscreenVideo: () -> Unit = {
    customViewCallback?.onCustomViewHidden()
    customViewCallback = null
    customView = null

    activity?.let { act ->
      act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
      val window = act.window
      val insetsController = WindowCompat.getInsetsController(window, window.decorView)
      insetsController.show(WindowInsetsCompat.Type.systemBars())
    }
  }

  // Handle Back Button press
  BackHandler(enabled = true) {
    when {
      customView != null -> {
        hideFullscreenVideo()
      }
      webViewInstance?.canGoBack() == true -> {
        webViewInstance?.goBack()
      }
      else -> {
        onExitApp()
      }
    }
  }

  // Manage activity lifecycle for WebView pause/resume
  DisposableEffect(Unit) {
    onDispose {
      webViewInstance?.apply {
        stopLoading()
        onPause()
      }
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(FilmyDarkBg)
  ) {
    if (customView != null) {
      // Fullscreen Video Layout
      AndroidView(
        factory = {
          customView ?: View(context)
        },
        modifier = Modifier
          .fillMaxSize()
          .background(FilmyDarkBg)
          .testTag("fullscreen_video_view")
      )
    } else {
      // Main WebView Layout with Edge-to-Edge inset padding
      val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(top = statusBarPadding)
      ) {
        AndroidView(
          factory = { ctx ->
            WebView(ctx).apply {
              layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
              )
              isFocusable = true
              isFocusableInTouchMode = true
              scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
              isScrollbarFadingEnabled = true
              // Use default layer type to avoid Mesa DRI rendernode allocation errors in virtualized environments
              setLayerType(View.LAYER_TYPE_NONE, null)
              setBackgroundColor(android.graphics.Color.parseColor("#0D0714"))

              // Configure WebView Settings
              settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = if (NetworkUtils.isNetworkAvailable(ctx)) {
                  WebSettings.LOAD_DEFAULT
                } else {
                  WebSettings.LOAD_CACHE_ELSE_NETWORK
                }
                allowFileAccess = true
                allowContentAccess = true
                mediaPlaybackRequiresUserGesture = false

                // High-performance page rendering optimizations
                loadsImagesAutomatically = true
                blockNetworkImage = false

                // Responsive mobile layout rules
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false

                // Popup & Multiple Windows Support
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)

                // HTTPS & Mixed Content
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                // Clean User Agent compatible with Cloudflare & modern streaming
                val defaultUa = userAgentString
                userAgentString = "$defaultUa Mobile"
              }

              // Cookie Configuration
              val cookieManager = CookieManager.getInstance()
              cookieManager.setAcceptCookie(true)
              cookieManager.setAcceptThirdPartyCookies(this, true)

              // Download Manager Setup
              setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                handleDownload(ctx, url, userAgent, contentDisposition, mimetype)
              }

              // Custom WebViewClient
              webViewClient = FilmyTVWebViewClient(
                context = ctx,
                onPageStartedCallback = {
                  if (progressValue < 0.7f) {
                    isLoading = true
                  }
                  hasNetworkError = false
                },
                onPageCommitVisibleCallback = {
                  // The earliest moment content is visually committed to screen
                  isLoading = false
                },
                onPageFinishedCallback = {
                  isLoading = false
                  CookieManager.getInstance().flush()
                },
                onErrorCallback = { failedUrl ->
                  hasNetworkError = true
                  failingUrl = failedUrl
                  isLoading = false
                }
              )

              // Custom WebChromeClient
              webChromeClient = FilmyTVWebChromeClient(
                onProgressChangedCallback = { progress ->
                  progressValue = progress / 100f
                  if (progress >= 70) {
                    isLoading = false
                  }
                },
                onShowCustomViewCallback = { view, callback ->
                  customView = view
                  customViewCallback = callback

                  activity?.let { act ->
                    act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    val window = act.window
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.hide(WindowInsetsCompat.Type.systemBars())
                    insetsController.systemBarsBehavior =
                      WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                  }
                },
                onHideCustomViewCallback = {
                  hideFullscreenVideo()
                },
                onShowFileChooserCallback = onShowFileChooser
              )

              // Initial load
              loadUrl(websiteUrl)
              webViewInstance = this
            }
          },
          update = { webView ->
            webViewInstance = webView
            webView.onResume()
            if (webView.url == null) {
              webView.loadUrl(websiteUrl)
            }
          },
          modifier = Modifier
            .fillMaxSize()
            .testTag("webview_container")
        )

        // Sleek hairline progress bar (auto-hides as soon as page renders)
        AnimatedVisibility(
          visible = isLoading && progressValue in 0.05f..0.7f && !hasNetworkError,
          enter = fadeIn(),
          exit = fadeOut(),
          modifier = Modifier.align(Alignment.TopCenter)
        ) {
          LinearProgressIndicator(
            progress = { progressValue },
            color = FilmyRed,
            trackColor = Color.Transparent,
            modifier = Modifier
              .fillMaxWidth()
              .height(2.dp)
              .testTag("loading_progress_bar")
          )
        }

        // Clean Mobile-Friendly Error Screen
        if (hasNetworkError) {
          ErrorScreen(
            onRetry = {
              if (NetworkUtils.isNetworkAvailable(context)) {
                hasNetworkError = false
                isLoading = true
                val retryUrl = failingUrl ?: websiteUrl
                webViewInstance?.loadUrl(retryUrl)
              } else {
                Toast.makeText(
                  context,
                  context.getString(R.string.offline_title),
                  Toast.LENGTH_SHORT
                ).show()
              }
            },
            modifier = Modifier
              .fillMaxSize()
              .testTag("error_screen")
          )
        }
      }
    }
  }
}

/**
 * Initiates an Android DownloadManager task with proper cookies and notifications
 */
private fun handleDownload(
  context: Context,
  url: String,
  userAgent: String,
  contentDisposition: String,
  mimetype: String
) {
  try {
    val request = DownloadManager.Request(Uri.parse(url)).apply {
      setMimeType(mimetype)
      val cookies = CookieManager.getInstance().getCookie(url)
      if (!cookies.isNullOrEmpty()) {
        addRequestHeader("cookie", cookies)
      }
      addRequestHeader("User-Agent", userAgent)
      setDescription("Downloading media from FilmyTV…")
      val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
      setTitle(fileName)
      setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
      setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
    }

    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
    if (downloadManager != null) {
      downloadManager.enqueue(request)
      Toast.makeText(
        context,
        context.getString(R.string.download_started),
        Toast.LENGTH_SHORT
      ).show()
    } else {
      Toast.makeText(
        context,
        context.getString(R.string.download_failed),
        Toast.LENGTH_SHORT
      ).show()
    }
  } catch (e: Exception) {
    Toast.makeText(
      context,
      context.getString(R.string.download_failed),
      Toast.LENGTH_SHORT
    ).show()
  }
}
