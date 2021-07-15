package com.apizee.webrtc.apiRTC

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import es.dmoral.toasty.Toasty
import kotlinx.android.synthetic.main.activity_lobby.*


class LobbyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_lobby)

        // Display version of app and SDK
        val version = "v${BuildConfig.VERSION_NAME}/${com.apizee.apiRTC.BuildConfig.LIBRARY_PACKAGE_NAME}-${com.apizee.apiRTC.BuildConfig.APIRTC_VERSION_NAME}"
        textVersion.text = version

        handlePermissions()

        buttonStart.setOnClickListener {
            val intent = when (spinnerTutorial.selectedItemPosition + 1) {
                1 -> Intent(this, TutorialConferencingVideo::class.java)
                2 -> Intent(this, TutorialConferencingChat::class.java)
                3 -> Intent(this, TutorialPeertopeerChat::class.java)
                4 -> Intent(this, TutorialPeertopeerSendData::class.java)
                5 -> Intent(this, TutorialPresenceGroupManagement::class.java)
                6 -> Intent(this, TutorialUserDataSharing::class.java)
                7 -> Intent(this, TutorialPeertopeerSendFile::class.java)
                8 -> Intent(this, PeerToPeerCall::class.java)
                else -> null
            }

            if (intent != null)
                startActivity(intent)
        }
    }

    private fun handlePermissions() {
        val canAccessCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val canRecordAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val canReadFiles = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        val canWriteFiles = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        if (!canAccessCamera || !canRecordAudio || !canReadFiles || !canWriteFiles) {
            // Missing permissions ; request them
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSIONS_REQUEST)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSIONS_REQUEST -> {
                var missing = false
                var i = 0
                while (i < permissions.size) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "Permission Granted: " + permissions[i])
                    } else if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        Log.e(TAG, "Permission Denied: " + permissions[i])
                        missing = true
                    }
                    i++
                }
                if (missing) {
                    toast(ToastyType.TOASTY_ERROR, "Permission denied. Exiting.")
                    finish()
                }
            }
            else -> {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            }
        }
    }

    private enum class ToastyType { TOASTY_ERROR, TOASTY_SUCCESS, TOASTY_INFO }

    private fun toast(type: ToastyType, message: String) {
        Log.d(TAG, "Toast message: $message")
        runOnUiThread {
            when (type) {
                ToastyType.TOASTY_ERROR -> Toasty.error(this, message, Toast.LENGTH_LONG).show()
                ToastyType.TOASTY_SUCCESS -> Toasty.success(this, message, Toast.LENGTH_SHORT).show()
                ToastyType.TOASTY_INFO -> Toasty.info(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val TAG = "LobbyActivity"
        private const val PERMISSIONS_REQUEST = 1220
    }
}
