package com.apizee.webrtc.apiRTC

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.apizee.apiRTC.*
import com.apizee.apiRTC.Conversation.Companion.EVENT_CONTACT_JOINED
import com.apizee.apiRTC.Conversation.Companion.EVENT_CONTACT_LEFT
import com.apizee.apiRTC.Conversation.Companion.EVENT_HANGUP
import com.apizee.apiRTC.Conversation.Companion.EVENT_MESSAGE
import com.apizee.apiRTC.Conversation.Companion.EVENT_STREAM_ADDED
import com.apizee.apiRTC.Conversation.Companion.EVENT_STREAM_LIST_CHANGED
import com.apizee.apiRTC.Conversation.Companion.EVENT_STREAM_REMOVED
import com.apizee.apiRTC.Session.Companion.EVENT_ERROR
import es.dmoral.toasty.Toasty
import kotlinx.android.synthetic.main.activity_video_call.*
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import java.util.concurrent.ConcurrentHashMap


class VideoCallActivity : AppCompatActivity() {
    private var localVideoView: SurfaceViewRenderer? = null
    private var audioManager: AudioManager? = null
    private var savedMicrophoneState: Boolean? = null
    private var savedSpeakerphoneState: Boolean? = null
    private var savedAudioMode: Int? = null

    private var ua: UserAgent? = null
    private var localStream: Stream? = null

    private var usedSurfaceViewRenderer: ConcurrentHashMap<Stream?, SurfaceViewRenderer?> = ConcurrentHashMap()
    private var freeSurfaceViewRenderer: MutableList<SurfaceViewRenderer> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handlePermissions()
    }

    private fun joinConference(server: String, apiKey: String?, name: String) {
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
        ua?.register(optionsRegister)?.then { itSession ->
            val session = itSession as Session
            Log.d(TAG, "Session successfully connected")
            val connectedSession = session

            connectedSession.on(EVENT_ERROR)
            {
                val message = it[0] as String
                toast(ToastyType.TOASTY_ERROR, "Received error '$message''")
                finish()
            }

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
                        connectedConversation?.subscribeToStream(streamInfo.streamId)
                                ?.then {
                                    Log.d(TAG, "subscribeToStream success")
                                }
                                ?.catch { itError ->
                                    val error = itError as String
                                    toast(ToastyType.TOASTY_ERROR, "subscribeToStream error : '$error'")
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

                // Allocate a SurfaceViewRenderer
                val videoView = getSurfaceViewRenderer(stream)
                if (videoView != null) {
                    // Attach stream to SurfaceViewRenderer
                    stream?.attachToElement(videoView)
                } else
                    toast(ToastyType.TOASTY_ERROR, "No more video view available for stream $stream")
            }

            connectedConversation?.on(EVENT_STREAM_REMOVED)
            {
                val stream: Stream? = it[0] as Stream?
                Log.d(TAG, "streamRemoved : $stream")

                // Find SurfaceRenderer used by this stream
                val videoView = getSurfaceViewRenderer(stream)
                // Detach stream from SurfaceRenderer
                stream?.detachFromElement(videoView)
                // Mark SurfaceViewRenderer as free to be reused
                putBackSurfaceViewRenderer(stream)
            }

            connectedConversation?.on(EVENT_HANGUP)
            {
                val event = it[0] as Conversation.EventStreamHangup
                Log.d(TAG, "Event: $EVENT_HANGUP")
                runOnUiThread {
                    toast(ToastyType.TOASTY_INFO, "Call terminated from ${event.from} with reason '${event.reason}'")
                }
            }

            //==============================
            // 5/ CREATE LOCAL STREAM
            //==============================
            createStream(switch_audio.isChecked, switch_video.isChecked, (cameraSelector.selectedItem as UserAgent.MediaDeviceList.MediaDevice?)?.id)

            // Handle received chat messages
            connectedConversation?.on(EVENT_MESSAGE)
            {
                val message = it[0] as Conversation.Message
                ChatActivity.addMessage(message.sender.getId(), message.content)
                toast(ToastyType.TOASTY_INFO, "Received message from '${message.sender.getId()}': '${message.content}'")
            }

            // ====================================
            // Handle contact events
            // ====================================
            connectedConversation?.on(EVENT_CONTACT_JOINED) {
                val contact = it[0] as Contact
                Log.d(TAG, "Contact that has joined : ${contact.getId()}")
                val contacts = connectedConversation?.getContacts()
                ChatActivity.setContacts(contacts)
            }

            connectedConversation?.on(EVENT_CONTACT_LEFT) {
                val contact = it[0] as Contact
                Log.d(TAG, "Contact that has left : ${contact.getId()}")
                val contacts = connectedConversation?.getContacts()
                ChatActivity.setContacts(contacts)
                finish()
            }
            if (connectedConversation != null)
                Log.d(TAG, "Conversation successfully connected")
        }?.catch {
            val error = it as String
            toast(ToastyType.TOASTY_ERROR, "User agent registration failed with '$error'")
            finish()
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
        setContentView(R.layout.activity_video_call)
        localVideoView = findViewById(R.id.pip_video)

        // Static provisioning of all available SurfaceViewRenderers.
        // They could be dynamically created too.
        addBackSurfaceViewRenderer(remote_video1)
        addBackSurfaceViewRenderer(remote_video2)
        addBackSurfaceViewRenderer(remote_video3)
        addBackSurfaceViewRenderer(remote_video4)
        addBackSurfaceViewRenderer(remote_video5)
        addBackSurfaceViewRenderer(remote_video6)
        addBackSurfaceViewRenderer(remote_video7)
        addBackSurfaceViewRenderer(remote_video8)
        addBackSurfaceViewRenderer(remote_video9)
        addBackSurfaceViewRenderer(remote_video10)
        addBackSurfaceViewRenderer(remote_video11)
        addBackSurfaceViewRenderer(remote_video12)
        addBackSurfaceViewRenderer(remote_video13)
        addBackSurfaceViewRenderer(remote_video14)
        addBackSurfaceViewRenderer(remote_video15)
        addBackSurfaceViewRenderer(remote_video16)

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

        // Handle chat window
        buttonChat.setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java)
            startActivity(intent)
        }

        interfaceCameraInit()
    }

    private fun createStream(audio: Boolean, video: Boolean, videoInputId: String?) {
        val createStreamOptions = UserAgent.CreateStreamOptions()
        createStreamOptions.constraints.audio = audio
        createStreamOptions.constraints.video = video
        createStreamOptions.videoInputId = videoInputId

        ua?.createStream(createStreamOptions)
                ?.then {
                    val stream = it as Stream
                    Log.d(TAG, "Create stream OK")
                    val oldStream = localStream
                    // Remove old stream
                    oldStream?.detachFromElement(localVideoView)
                    oldStream?.release()
                    // Save local stream
                    localStream = stream
                    // Attach stream
                    stream.attachToElement(localVideoView)

                    connectedConversation?.replacePublishedStream(localStream, oldStream)

                    //==============================
                    // 6/ JOIN CONVERSATION
                    //==============================
                    connectedConversation?.join()
                            ?.then {
                                //==============================
                                // 7/ PUBLISH OWN STREAM
                                //==============================
                                connectedConversation?.publish(localStream)
                                Log.d(TAG, "Published local stream")
                            }?.catch { itError ->
                                val error = itError as String
                                toast(ToastyType.TOASTY_ERROR, "Conversation join error : '$error'")

                            }
                    true
                }?.catch {
                    toast(ToastyType.TOASTY_ERROR, "Create stream failed")
                }
    }

    private fun updateStream(audio: Boolean, video: Boolean, videoInputId: String?) {
        val createStreamOptions = UserAgent.CreateStreamOptions()
        createStreamOptions.constraints.audio = audio
        createStreamOptions.constraints.video = video
        createStreamOptions.videoInputId = videoInputId

        if (connectedConversation == null)
            return

        ua?.createStream(createStreamOptions)
                ?.then {
                    val stream = it as Stream
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
                    stream.attachToElement(localVideoView)
                }
                ?.catch {
                    toast(ToastyType.TOASTY_ERROR, "Create stream failed")
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
    }

    private enum class ToastyType { TOASTY_ERROR, TOASTY_SUCCESS, TOASTY_INFO }

    private fun toast(type: ToastyType, message: String) {
        Log.d(TAG, "Toast message: $message")
        runOnUiThread {
            when (type) {
                ToastyType.TOASTY_ERROR -> Toasty.error(this, message, Toast.LENGTH_SHORT).show()
                ToastyType.TOASTY_SUCCESS -> Toasty.success(this, message, Toast.LENGTH_SHORT).show()
                ToastyType.TOASTY_INFO -> Toasty.info(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        toast(ToastyType.TOASTY_INFO, "Call terminated")

        localStream?.release()

        ua?.unregister()
        localVideoView?.release()

        for ((stream, _) in usedSurfaceViewRenderer) {
            putBackSurfaceViewRenderer(stream)
        }

        audioTerminate()

        connectedConversation = null

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
            toast(ToastyType.TOASTY_ERROR, "Invalid room")
            return
        }
        if (server == null) {
            toast(ToastyType.TOASTY_ERROR, "Invalid server")
            return
        }
        if (apiKey == null) {
            toast(ToastyType.TOASTY_ERROR, "Invalid apiKey")
            return
        }

        toast(ToastyType.TOASTY_INFO, "Starting call to '$room'")

        joinConference(server, apiKey, room)
    }

    private fun getSurfaceViewRenderer(stream: Stream?): SurfaceViewRenderer? {
        if (stream == null)
            return null

        var surfaceViewRenderer = usedSurfaceViewRenderer[stream]

        // Found already allocated surfaceViewRenderer for this stream. Reuse it.
        if (surfaceViewRenderer != null)
            return surfaceViewRenderer

        if (freeSurfaceViewRenderer.isNotEmpty()) {
            surfaceViewRenderer = freeSurfaceViewRenderer[0]
        }

        // Oops, no more surfaceViewRenderer
        if (surfaceViewRenderer == null)
            return null

        Log.d(TAG, "Allocate surfaceViewRenderer: $surfaceViewRenderer")

        freeSurfaceViewRenderer.remove(surfaceViewRenderer)
        usedSurfaceViewRenderer[stream] = surfaceViewRenderer


        runOnUiThread {
            surfaceViewRenderer.visibility = View.VISIBLE
            surfaceViewRenderer.init(UserAgent.getEglBaseContext(), null)
            // TODO: Activate hardware scaler?
            surfaceViewRenderer.setEnableHardwareScaler(true)

            surfaceViewRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        }

        return surfaceViewRenderer
    }

    private fun putBackSurfaceViewRenderer(stream: Stream?) {
        val surfaceViewRenderer = usedSurfaceViewRenderer.remove(stream)
        if (surfaceViewRenderer != null) {
            Log.d(TAG, "putBackSurfaceViewRenderer: $surfaceViewRenderer")
            freeSurfaceViewRenderer.add(0, surfaceViewRenderer)
            runOnUiThread {
                surfaceViewRenderer.release()
                surfaceViewRenderer.visibility = View.GONE
            }
        } else {
            Log.w(TAG, "putBackSurfaceViewRenderer: Stream $stream not found")
        }
    }

    private fun addBackSurfaceViewRenderer(surfaceViewRenderer: SurfaceViewRenderer) {
        freeSurfaceViewRenderer.add(surfaceViewRenderer)
        runOnUiThread {
            surfaceViewRenderer.visibility = View.GONE
        }
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
                    toast(ToastyType.TOASTY_SUCCESS, "Camera permission granted")
                    joinConference()
                } else {
                    // Permissions refused ; terminate call
                    toast(ToastyType.TOASTY_ERROR, "Camera permission denied")
                    finish()
                }
                return
            }
        }
    }

    companion object {
        private const val CAMERA_AUDIO_PERMISSION_REQUEST = 1
        private const val TAG = "VideoCallActivity"

        private var connectedConversation: Conversation? = null

        fun sendMessage(source: String, message: String) {
            if (connectedConversation != null) {
                // Send message to network
                connectedConversation?.sendMessage(message)
                // Display message locally
                ChatActivity.addMessage(source, message)
            } else {
                // Display error message locally
                ChatActivity.addMessage("*System*", "Can't send message, no active conversation.")
            }
        }
    }
}
