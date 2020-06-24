package apizee.webrtc.izeeconf

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_lobby.*
import kotlin.random.Random


class LobbyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_lobby)

        // Display version of app and SDK
        val version : String = "" + getText(R.string.version) + " / " + com.apizee.apiRTC.BuildConfig.LIBRARY_PACKAGE_NAME + "-" + com.apizee.apiRTC.BuildConfig.VERSION_NAME
        textVersion.text = version

        // Choose an easy to remember random room name
        val defaultRoom = "conf-" + Random.nextLong(1000, 9999).toString()
        textRoom.setText(defaultRoom)

        connectButton.setOnClickListener {
            val server = findViewById<Spinner>(R.id.spinnerServer).selectedItem.toString()
            val room = findViewById<EditText>(R.id.textRoom).text.toString()

            startVideoCall(server, room, "myDemoApiKey")
        }
    }

    private fun startVideoCall(server: String, room: String, apiKey: String) {
        val intent = Intent(this, VideoCallActivity::class.java)
        intent.putExtra("server", server)
        intent.putExtra("room", room)
        intent.putExtra("apiKey", apiKey)
        startActivity(intent)
    }
}
