package apizee.webrtc.izeeconf

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.apizee.apiRTC.Conversation
import com.apizee.apiRTC.Conversation.Companion.EVENT_HANGUP
import com.apizee.apiRTC.Conversation.Companion.EVENT_STREAM_ADDED
import com.apizee.apiRTC.Conversation.Companion.EVENT_STREAM_LIST_CHANGED
import com.apizee.apiRTC.Conversation.Companion.EVENT_STREAM_REMOVED
import com.apizee.apiRTC.Stream
import com.apizee.apiRTC.UserAgent
import kotlinx.android.synthetic.main.activity_video_call.*
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer


class VideoCallActivity : AppCompatActivity() {
    private var statusTextView: TextView? = null
    private var localVideoView: SurfaceViewRenderer? = null
    private var remoteVideoView: SurfaceViewRenderer? = null
    private var audioManager: AudioManager? = null
    private var savedMicrophoneState: Boolean? = null
    private var savedSpeakerphoneState: Boolean? = null
    private var savedAudioMode: Int? = null

    private var ua: UserAgent? = null
    private var localStream: Stream? = null
    private var connectedConversation: Conversation? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handlePermissions()
    }

    private fun joinConference(server: String, apiKey: String?, name: String) {
        setContentView(R.layout.activity_video_call)
        statusTextView = findViewById(R.id.status_text)
        localVideoView = findViewById(R.id.pip_video)
        remoteVideoView = findViewById(R.id.remote_video)


        //==============================
        // 1/ CREATE USER AGENT
        //==============================
        val optionsUa = UserAgent.UserAgentOptions(uri = "apzkey:$apiKey")
        ua = UserAgent(this, optionsUa)

        // Init UI, audio and video
        interfaceInit()
        audioInit()
        videoInit()

        //==============================
        // 2/ REGISTER
        //==============================
        val optionsRegister = UserAgent.RegisterInformation(cloudUrl = server)
        ua?.register(optionsRegister) { result, session ->

            when (result) {
                UserAgent.Result.OK -> {
                    Log.d(TAG, "Session successfully connected")
                    val connectedSession = session ?: return@register

                    //==============================
                    // 3/ CREATE CONVERSATION
                    //==============================
                    connectedConversation = connectedSession.getOrCreateConversation(name)

                    //==========================================================
                    // 4/ ADD EVENT LISTENER : WHEN NEW STREAM IS AVAILABLE IN CONVERSATION
                    //==========================================================
                    connectedConversation?.on(EVENT_STREAM_LIST_CHANGED)
                    {
                        val streamInfo: Conversation.StreamInfo = it[0] as Conversation.StreamInfo
                        Log.d(TAG, "streamListChanged : $streamInfo")

                        if (streamInfo.listEventType == "added") {
                            if (streamInfo.isRemote) {
                                connectedConversation?.subscribeToStream(streamInfo.streamId) { status, stream ->
                                    when (status) {
                                        Conversation.Result.OK -> {
                                            Log.d(TAG, "subscribeToStream success")
                                        }
                                        Conversation.Result.FAILED -> {
                                            toast("subscribeToStream error")
                                        }
                                    }
                                }
                            }
                        }
                    }


                    //=====================================================
                    // 4 BIS/ ADD EVENT LISTENER : WHEN STREAM WAS REMOVED FROM THE CONVERSATION
                    //=====================================================
                    connectedConversation?.on(EVENT_STREAM_ADDED)
                    {
                        val stream: Stream? = it[0] as Stream?
                        Log.d(TAG, "streamAdded : $stream")
                        stream?.attachToElement(remoteVideoView)
                    }

                    connectedConversation?.on(EVENT_STREAM_REMOVED)
                    {
                        val stream: Stream? = it[0] as Stream?
                        Log.d(TAG, "streamRemoved : $stream")
                        stream?.detachFromElement(remoteVideoView)
                    }

                    connectedConversation?.on(EVENT_HANGUP)
                    {
                        val event = it[0] as Conversation.EventStreamHangup
                        Log.d(TAG, "Event: $EVENT_HANGUP")
                        runOnUiThread {
                            toast("Call terminated from ${event.from} with reason '${event.reason}'")
                        }
                        finish()
                    }

                    //==============================
                    // 5/ CREATE LOCAL STREAM
                    //==============================
                    createStream(switch_audio.isChecked, switch_video.isChecked, (cameraSelector.selectedItem as UserAgent.MediaDeviceList.MediaDevice?)?.id)
                }
                UserAgent.Result.FAILED -> {
                    Log.d(TAG, "Session creation failed!")
                    toast("Register failed")
                    finish()
                }
            }
        }
    }

    private fun interfaceCameraInit() {
        val cameras = ua?.getUserMediaDevices()?.videoinput

        Log.d(TAG, "Available cameras: '$cameras'")

        if (cameras != null) {
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, cameras)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            cameraSelector.adapter = adapter

            cameraSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}

                override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                    // either one will work as well
                    // val item = parent.getItemAtPosition(position) as String
                    val item = adapter.getItem(position)
                    Log.d(TAG, "Selected camera '$item' at position $position")

                    updateStream(switch_audio.isChecked, switch_video.isChecked, position.toString())
                }
            }
        }
    }

    private fun interfaceInit() {
        hangup_button.setOnClickListener {
            finish()
        }

        // Handle video mute
        switch_video.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                localStream?.unmuteVideo()
                cameraSelector.visibility = View.VISIBLE
            } else {
                localStream?.muteVideo()
                // Hide camera selector when sending of video is disabled
                cameraSelector.visibility = View.INVISIBLE
            }
        }

        // Handle audio mute
        switch_audio.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked)
                localStream?.unmuteAudio()
            else
                localStream?.muteAudio()
        }

        // Handle speaker on/off
        switch_speaker.setOnCheckedChangeListener { _, isChecked ->
            audioManager?.isSpeakerphoneOn = isChecked
        }

        interfaceCameraInit()
    }

    private fun createStream(audio: Boolean, video: Boolean, videoInputId: String?) {
        val createStreamOptions = UserAgent.CreateStreamOptions()
        createStreamOptions.constraints.audio = audio
        createStreamOptions.constraints.video = video
        createStreamOptions.videoInputId = videoInputId

        ua?.createStream(createStreamOptions) { status, stream ->
            when (status) {
                UserAgent.Result.OK -> {
                    Log.d(TAG, "Create stream OK")
                    val oldStream = localStream
                    // Remove old stream
                    oldStream?.detachFromElement(localVideoView)
                    oldStream?.release()
                    // Save local stream
                    localStream = stream
                    // Attach stream
                    stream?.attachToElement(localVideoView)

                    connectedConversation?.replacePublishedStream(localStream, oldStream)

                    //==============================
                    // 6/ JOIN CONVERSATION
                    //==============================
                    connectedConversation?.join { status, event ->
                        when (status) {
                            Conversation.Result.OK -> {
                                //==============================
                                // 7/ PUBLISH OWN STREAM
                                //==============================
                                Log.d(TAG, "Received event joined with $event")
                                connectedConversation?.publish(localStream)
                            }
                            Conversation.Result.FAILED -> {
                                toast("Conversation join error")
                            }
                        }
                    }
                }
                UserAgent.Result.FAILED -> {
                    toast("Create stream failed")
                }
            }
        }
    }

    private fun updateStream(audio: Boolean, video: Boolean, videoInputId: String?) {
        val createStreamOptions = UserAgent.CreateStreamOptions()
        createStreamOptions.constraints.audio = audio
        createStreamOptions.constraints.video = video
        createStreamOptions.videoInputId = videoInputId

        if (connectedConversation == null)
            return

        ua?.createStream(createStreamOptions) { status, stream ->
            when (status) {
                UserAgent.Result.OK -> {
                    Log.d(TAG, "Create stream OK")
                    val oldStream = localStream
                    // Save local stream
                    localStream = stream

                    if (oldStream !== null) {
                        // Remove old stream
                        oldStream.detachFromElement(localVideoView)
                        oldStream.release()

                        val call = connectedConversation?.getConversationCall(oldStream)
                        call?.replacePublishedStream(localStream)
                    }

                    // Attach stream
                    stream?.attachToElement(localVideoView)
                }
                UserAgent.Result.FAILED -> {
                    toast("Create stream failed")
                }
            }
        }
    }

    private fun audioInit() {
        audioManager = this.getSystemService(Context.AUDIO_SERVICE) as AudioManager?
        savedAudioMode = audioManager?.mode
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION

        savedMicrophoneState = audioManager?.isMicrophoneMute
        savedSpeakerphoneState = audioManager?.isSpeakerphoneOn

        audioManager?.isMicrophoneMute = false
        audioManager?.isSpeakerphoneOn = true
    }

    private fun audioTerminate() {
        if (savedAudioMode !== null) {
            audioManager?.mode = savedAudioMode!!
        }
        if (savedMicrophoneState != null) {
            audioManager?.isMicrophoneMute = savedMicrophoneState!!
        }
        if (savedSpeakerphoneState != null) {
            audioManager?.isSpeakerphoneOn = savedSpeakerphoneState!!
        }
    }

    private fun videoInit() {
        localVideoView?.init(UserAgent.getEglBaseContext(), null)
        localVideoView?.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        localVideoView?.setZOrderMediaOverlay(true)
        localVideoView?.setEnableHardwareScaler(true)
        localVideoView?.setMirror(false)

        remoteVideoView?.init(UserAgent.getEglBaseContext(), null)
        remoteVideoView?.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        remoteVideoView?.setEnableHardwareScaler(true)

    }

    private fun toast(message: String) {
        Log.d(TAG, "Toast message: $message")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        toast("Call terminated")

        localStream?.release()

        ua?.unregister()
        localVideoView?.release()
        remoteVideoView?.release()

        audioTerminate()

        super.onDestroy()
    }

    override fun onStop() {
        Log.d(TAG, "onStop()")
        super.onStop()
    }

    private fun joinConference() {
        val server = intent.getStringExtra("server")
        val room = intent.getStringExtra("room")
        val apiKey = intent.getStringExtra("apiKey")

        if (room == null) {
            toast("Invalid room")
            return
        }
        if (server == null) {
            toast("Invalid server")
            return
        }
        if (apiKey == null) {
            toast("Invalid apiKey")
            return
        }

        toast("Starting call to '$room'")

        joinConference(server, apiKey, room)
    }

    private fun handlePermissions() {
        val canAccessCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val canRecordAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!canAccessCamera || !canRecordAudio) {
            // Missing permissions ; request them
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), CAMERA_AUDIO_PERMISSION_REQUEST)
        } else {
            // Permissions OK ; join conference
            joinConference()
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        Log.w(TAG, "onRequestPermissionsResult: $requestCode $permissions $grantResults")
        when (requestCode) {
            CAMERA_AUDIO_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults.first() == PackageManager.PERMISSION_GRANTED) {
                    // Permissions OK ; join conference
                    toast("Camera permission granted")
                    joinConference()
                } else {
                    // Permissions refused ; terminate call
                    toast("Camera permission denied")
                    finish()
                }
                return
            }
        }
    }

    companion object {
        private const val CAMERA_AUDIO_PERMISSION_REQUEST = 1
        private const val TAG = "VideoCallActivity"
    }
}
