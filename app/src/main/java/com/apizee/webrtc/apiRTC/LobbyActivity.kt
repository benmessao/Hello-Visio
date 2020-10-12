package com.apizee.webrtc.apiRTC

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_lobby.*


class LobbyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_lobby)

        // Display version of app and SDK
        val version = "v${BuildConfig.VERSION_NAME}/${com.apizee.apiRTC.BuildConfig.LIBRARY_PACKAGE_NAME}-${com.apizee.apiRTC.BuildConfig.VERSION_NAME}"
        textVersion.text = version

        buttonStart.setOnClickListener {
            val intent = when (spinnerTutorial.selectedItemPosition + 1) {
                1 -> Intent(this, TutorialConferencingVideo::class.java)
                2 -> Intent(this, TutorialConferencingChat::class.java)
                3 -> Intent(this, TutorialPeertopeerChat::class.java)
                4 -> Intent(this, TutorialPeertopeerSendData::class.java)
                5 -> Intent(this, TutorialPresenceGroupManagement::class.java)
                6 -> Intent(this, TutorialUserDataSharing::class.java)
                else -> null
            }

            if (intent != null)
                startActivity(intent)
        }
    }
}
