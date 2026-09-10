package com.example.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.FilmyDarkBg
import com.example.ui.theme.FilmyDarkSurface
import com.example.ui.theme.FilmyLightText
import com.example.ui.theme.FilmyMutedText
import com.example.ui.theme.FilmyRed

@Composable
fun ErrorScreen(
  onRetry: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(FilmyDarkBg)
      .padding(32.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.fillMaxWidth()
    ) {
      // Cinema-styled warning icon badge
      Box(
        modifier = Modifier
          .size(96.dp)
          .clip(CircleShape)
          .background(FilmyDarkSurface),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.SignalWifiOff,
          contentDescription = stringResource(R.string.offline_title),
          tint = FilmyRed,
          modifier = Modifier.size(48.dp)
        )
      }

      Spacer(modifier = Modifier.height(28.dp))

      Text(
        text = stringResource(R.string.offline_title),
        color = FilmyLightText,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = stringResource(R.string.offline_message),
        color = FilmyMutedText,
        fontSize = 15.sp,
        textAlign = TextAlign.Center,
        lineHeight = 22.sp,
        modifier = Modifier.padding(horizontal = 16.dp)
      )

      Spacer(modifier = Modifier.height(32.dp))

      // Primary Retry Button
      Button(
        onClick = onRetry,
        colors = ButtonDefaults.buttonColors(
          containerColor = FilmyRed,
          contentColor = FilmyLightText
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth(0.75f)
          .height(50.dp)
          .testTag("retry_button")
      ) {
        Icon(
          imageVector = Icons.Default.Refresh,
          contentDescription = null,
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
          text = stringResource(R.string.retry),
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Network Settings Button
      OutlinedButton(
        onClick = {
          openNetworkSettings(context)
        },
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
          contentColor = FilmyMutedText
        ),
        modifier = Modifier
          .fillMaxWidth(0.75f)
          .height(46.dp)
          .testTag("settings_button")
      ) {
        Text(
          text = "Check Network Settings",
          fontSize = 14.sp
        )
      }
    }
  }
}

private fun openNetworkSettings(context: Context) {
  try {
    val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  } catch (e: Exception) {
    val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(fallbackIntent)
  }
}
