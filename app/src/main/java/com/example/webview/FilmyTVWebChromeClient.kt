package com.example.webview

import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

class FilmyTVWebChromeClient(
  private val onProgressChangedCallback: (progress: Int) -> Unit,
  private val onShowCustomViewCallback: (view: View?, callback: CustomViewCallback?) -> Unit,
  private val onHideCustomViewCallback: () -> Unit,
  private val onShowFileChooserCallback: (
    filePathCallback: ValueCallback<Array<Uri>>?,
    fileChooserParams: FileChooserParams?
  ) -> Boolean
) : WebChromeClient() {

  override fun onProgressChanged(view: WebView?, newProgress: Int) {
    super.onProgressChanged(view, newProgress)
    onProgressChangedCallback(newProgress)
  }

  override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
    super.onShowCustomView(view, callback)
    onShowCustomViewCallback(view, callback)
  }

  override fun onHideCustomView() {
    super.onHideCustomView()
    onHideCustomViewCallback()
  }

  override fun onShowFileChooser(
    webView: WebView?,
    filePathCallback: ValueCallback<Array<Uri>>?,
    fileChooserParams: FileChooserParams?
  ): Boolean {
    return onShowFileChooserCallback(filePathCallback, fileChooserParams)
  }

  override fun onCreateWindow(
    view: WebView?,
    isDialog: Boolean,
    isUserGesture: Boolean,
    resultMsg: Message?
  ): Boolean {
    // Safely handle target="_blank" or window.open calls by routing within the primary WebView
    val hrefMsg = view?.handler?.obtainMessage()
    view?.requestFocusNodeHref(hrefMsg)
    val url = hrefMsg?.data?.getString("url")
    if (!url.isNullOrEmpty()) {
      view.loadUrl(url)
      return true
    }

    val transport = resultMsg?.obj as? WebView.WebViewTransport
    if (transport != null && view != null) {
      transport.webView = view
      resultMsg.sendToTarget()
      return true
    }

    return super.onCreateWindow(view, isDialog, isUserGesture, resultMsg)
  }

  override fun onPermissionRequest(request: PermissionRequest?) {
    // Handle HTML5 permissions (e.g. video / audio / protected media)
    request?.let {
      try {
        it.grant(it.resources)
      } catch (e: Exception) {
        it.deny()
      }
    }
  }
}
