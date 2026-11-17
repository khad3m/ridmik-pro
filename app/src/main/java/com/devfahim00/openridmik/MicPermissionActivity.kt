package com.devfahim00.openridmik

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

/**
 * An IME service cannot ask for permissions itself, so the mic button opens this
 * invisible activity just to host the system permission dialog. After the answer
 * the voice flow resumes (or a helpful hint is shown) and the activity finishes.
 */
class MicPermissionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            resumeVoice()
            finish()
            return
        }
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode != 1) return
        if (results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) {
            resumeVoice()
        } else {
            OpenRidmikService.live?.onMicPermissionDenied()
            if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            // "Don't ask again" was picked: send the user to the app info page where the mic can be enabled
            try {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (e: Exception) {
            }
            Toast.makeText(this, "Enable Microphone permission for voice typing", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Microphone permission is needed for voice typing", Toast.LENGTH_LONG).show()
            }
        }
        finish()
    }

    private fun resumeVoice() {
        OpenRidmikService.live?.onMicPermissionGranted()
    }
}
