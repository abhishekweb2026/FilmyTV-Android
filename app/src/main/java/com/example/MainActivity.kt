package com.example

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.ui.FilmyTVScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

  companion object {
    const val DEFAULT_WEBSITE_URL = "https://filmytv.webapps.in.net/"
  }

  private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

  private val fileChooserLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == Activity.RESULT_OK) {
        val data = result.data
        val uris: Array<Uri>? = when {
          data?.data != null -> arrayOf(data.data!!)
          data?.clipData != null -> {
            val clipData = data.clipData!!
            Array(clipData.itemCount) { index -> clipData.getItemAt(index).uri }
          }
          else -> null
        }
        fileUploadCallback?.onReceiveValue(uris)
      } else {
        fileUploadCallback?.onReceiveValue(null)
      }
      fileUploadCallback = null
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    val splashScreen = installSplashScreen()
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      MyApplicationTheme {
        FilmyTVScreen(
          websiteUrl = getString(R.string.website_url),
          onShowFileChooser = { filePathCallback, fileChooserParams ->
            launchFileChooser(filePathCallback, fileChooserParams)
          },
          onExitApp = {
            finish()
          },
          modifier = Modifier.fillMaxSize()
        )
      }
    }
  }

  private fun launchFileChooser(
    filePathCallback: ValueCallback<Array<Uri>>?,
    fileChooserParams: WebChromeClient.FileChooserParams?
  ): Boolean {
    // Cancel any ongoing previous callback to prevent hanging WebViews
    fileUploadCallback?.onReceiveValue(null)
    fileUploadCallback = filePathCallback

    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
      type = "*/*"
      addCategory(Intent.CATEGORY_OPENABLE)
    }

    // Support multiple files if requested by HTML5 input
    if (fileChooserParams?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
      intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
    }

    val chooserIntent = Intent.createChooser(intent, getString(R.string.choose_file))

    return try {
      fileChooserLauncher.launch(chooserIntent)
      true
    } catch (e: Exception) {
      fileUploadCallback?.onReceiveValue(null)
      fileUploadCallback = null
      false
    }
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
  MyApplicationTheme { Greeting("FilmyTV") }
}

