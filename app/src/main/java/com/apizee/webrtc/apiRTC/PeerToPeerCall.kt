package com.apizee.webrtc.apiRTC

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.apizee.apiRTC.Contact
import com.apizee.apiRTC.Conversation
import com.apizee.apiRTC.Session
import com.apizee.apiRTC.UserAgent
import es.dmoral.toasty.Toasty
import kotlinx.android.synthetic.main.tutorial_conferencing_chat.*
import kotlinx.android.synthetic.main.tutorial_conferencing_video.*
import kotlinx.android.synthetic.main.tutorial_peertopeer_chat.*
import kotlinx.android.synthetic.main.tutorial_peertopeer_chat.textId
import kotlinx.android.synthetic.main.tutorial_peertopeer_send_data.*
import kotlin.random.Random
import kotlin.random.Random.Default.nextInt

class PeerToPeerCall : AppCompatActivity(){
    private var ua: UserAgent? = null
    private var cloudUrl = "https://cloud.apizee.com"
    private var connectedSession: Session? = null
    private var activeContact: Contact? = null
    private var activeConversation: Conversation? = null
    private var activePushDataId: String? = null
    private var listContactsString = arrayListOf<String>()
    private var listContactsAdapter: ArrayAdapter<*>? = null
    private var available: Boolean = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.peertopeer)


        val callButton = findViewById<Button>(R.id.callPeer)
        val peerId = findViewById<EditText>(R.id.peerId)
        val bAvailable = findViewById<Button>(R.id.redButton)

        bAvailable.setOnClickListener {
            available = !(available)
            if (available){
                bAvailable.setBackgroundResource(R.drawable.round_green_button)
                //bAvailable.text = "En ligne"
            } else{
                bAvailable.setBackgroundResource(R.drawable.round_red_button)
                //bAvailable.text = "Ne pas déranger"
            }
        }

        callButton.setOnClickListener{
            //Get contact ID
            val id = peerId.text.toString()
            setActiveContact(id)

            //Create & join conference room
            val roomId = "conf-" + Random.nextLong(1000, 9999).toString()
            startVideoCall("https://cloud.apizee.com", roomId, "b0dfc52e7766516f2efdebeb990d006c")

            //Send message to contact with conference room ID
            sendMessageToActiveContact(roomId)
            }
        start()
        }

        private fun startVideoCall(server: String, room: String, apiKey: String) {
            val intent = Intent(this, VideoCallActivity::class.java)
            intent.putExtra("server", server)
            intent.putExtra("room", room)
            intent.putExtra("apiKey", apiKey)
            startActivity(intent)
        }

    private fun start() {
        //==============================
        // CREATE USER AGENT
        //==============================
        val optionsUa = UserAgent.UserAgentOptions(uri = "apzkey:b0dfc52e7766516f2efdebeb990d006c")
        ua = UserAgent(this, optionsUa)

        //==============================
        // REGISTER
        //==============================
        val idTest = nextInt(9999999999999.toInt())
        val optionsRegister = UserAgent.RegisterInformation(cloudUrl = cloudUrl, id = idTest.toLong())
        ua?.register(optionsRegister)?.then {
            val session = it as Session
            Log.d(TAG, "Session successfully connected")
            connectedSession = session

            //==============================
            // WHEN CHAT MESSAGE IS  RECEIVED
            //==============================
            //Listen to contact message events globally
                connectedSession?.on(Session.EVENT_CONTACT_MESSAGE)
                {
                    val message = it[0] as Session.Message

                    //Save contact to contact list
                    val roomId = message.content
                    if (available){
                    startVideoCall("https://cloud.apizee.com", roomId, "b0dfc52e7766516f2efdebeb990d006c")}
                }


            runOnUiThread {
                // Display user number
                textId.text = "ID : ${session.getId()}"
            }
        }?.catch {
            val error = it as String
            toast(ToastyType.TOASTY_ERROR, "User agent registration failed with '$error'")
            finish()
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
        private const val TAG = "PeerToPeerCall"
    }

    private fun setActiveContact(contactId: String) {
        //==============================
        // GET CONTACT OBJECT
        //==============================
        val contact = connectedSession?.getOrCreateContact(contactId)
        activeContact = contact
    }

    private fun sendMessageToActiveContact(message: String) {
        val contact = activeContact
        if (message != "" && contact != null) {
            //Actually send message to active contact
            contact.sendMessage(message).then {
                //Message successfully sent!
            }.catch {
                //An error occurred...
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        //ua?.unregister()
        //connectedSession = null
    }


    }
