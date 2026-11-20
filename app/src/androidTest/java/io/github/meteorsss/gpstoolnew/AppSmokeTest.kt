package io.github.meteorsss.gpstoolnew

import android.Manifest
import android.graphics.Bitmap
import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import java.io.File
import org.junit.Rule
import org.junit.Test

class AppSmokeTest {
    @get:Rule(order = 0) val permissions = GrantPermissionRule.grant(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name + ".png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GPS-Tool-New-test")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        instrumentation.uiAutomation.takeScreenshot().let { image ->
            resolver.openOutputStream(uri)!!.use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
    }
    @Test fun fourTabsAndForegroundRecordingWork() {
        compose.onNodeWithTag("hero-speed").assertExists()
        screenshot("01-home")
        compose.onNodeWithTag("nav-1").performClick()
        compose.onNodeWithTag("satellite-screen").assertExists()
        screenshot("02-satellites")
        compose.onNodeWithTag("nav-2").performClick()
        compose.onNodeWithTag("track-screen").assertExists()
        screenshot("03-tracks")
        compose.onNodeWithTag("start-recording").performClick()
        val app = compose.activity.application as ToolboxApp
        compose.waitUntil(15_000) { app.recording.value != null }
        compose.onNodeWithText("暂停", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) { app.recording.value?.state == "paused" }
        compose.onNodeWithText("继续记录", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) { app.recording.value?.state == "recording" }
        compose.onNodeWithTag("stop-recording").performClick()
        compose.onNodeWithTag("confirm-stop").performClick()
        compose.waitUntil(10_000) { app.recording.value == null }
        compose.onNodeWithTag("nav-3").performClick()
        compose.onNodeWithTag("tools-screen").assertExists()
        screenshot("04-tools")
        compose.onNodeWithText("指南针").performClick()
        compose.onNodeWithText("请平放手机，远离磁铁和金属物体").assertExists()
        screenshot("05-compass")
    }
    @Test fun themeCanBeChanged() {
        compose.onNodeWithTag("open-settings").performClick()
        compose.onNodeWithText("浅色 / 户外").performClick()
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithTag("hero-speed").assertExists()
        screenshot("06-light-theme")
    }
}
