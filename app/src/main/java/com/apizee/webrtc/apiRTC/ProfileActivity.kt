package com.apizee.webrtc.apiRTC

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.appcompat.app.ActionBar
import com.apizee.webrtc.apiRTC.databinding.ActivityProfileBinding
import com.google.firebase.auth.FirebaseAuth
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.apizee.apiRTC.Contact
import com.apizee.apiRTC.Session
import com.apizee.apiRTC.UserAgent
import es.dmoral.toasty.Toasty
import kotlinx.android.synthetic.main.activity_profile.*
import kotlinx.android.synthetic.main.tutorial_peertopeer_chat.textId
import kotlin.random.Random

class ProfileActivity : AppCompatActivity() {
    //ViewBinding
    private lateinit var binding: ActivityProfileBinding
    //ActionBar
    private lateinit var actionBar: ActionBar
    //FirebaseAuth
    private lateinit var firebaseAuth: FirebaseAuth
    private var ua: UserAgent? = null
    private var cloudUrl = "https://cloud.apizee.com"
    private var connectedSession: Session? = null
    private var activeContact: Contact? = null
    private var available: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        handlePermissions()

        //configure action bar
        //actionBar = supportActionBar!!
        //actionBar.title = "Profile"

        //init firebase auth
        firebaseAuth = FirebaseAuth.getInstance()
        checkUser()

        //handle click, logout
        binding.logoutBtn.setOnClickListener {
            firebaseAuth.signOut()
            checkUser()
        }

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
            val id = stringToLong(peerId.text.toString()).toString()
            setActiveContact(id)

            //Create & join conference room
            val roomId = "conf-" + Random.nextLong(1000, 9999).toString()
            startVideoCall("https://cloud.apizee.com", roomId, "b0dfc52e7766516f2efdebeb990d006c")

            //Send message to contact with conference room ID
            sendMessageToActiveContact(roomId)
        }
        start()





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
        val myId = stringToLong(emailTv.text.toString())
        val optionsRegister = UserAgent.RegisterInformation(cloudUrl = cloudUrl, id = myId)
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

            }
        }?.catch {
            val error = it as String
            toast(ToastyType.TOASTY_ERROR, "User agent registration failed with '$error'")
            finish()
        }
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

    private fun setActiveContact(contactId: String) {
        //==============================
        // GET CONTACT OBJECT
        //==============================
        val contact = connectedSession?.getOrCreateContact(contactId)
        activeContact = contact
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

    private fun startVideoCall(server: String, room: String, apiKey: String) {
        val intent = Intent(this, VideoCallActivity::class.java)
        intent.putExtra("server", server)
        intent.putExtra("room", room)
        intent.putExtra("apiKey", apiKey)
        startActivity(intent)
    }

    private fun checkUser() {
        //check user is logged in or not
        val firebaseUser = firebaseAuth.currentUser
        if (firebaseUser != null ){
            //user is logged in, get user info
            val email = firebaseUser.email
            //set to text view
            binding.emailTv.text = email

        }
        else{
            //user is not logged in go tologin page
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun handlePermissions() {
        val canAccessCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val canRecordAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val canReadFiles = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        val canWriteFiles = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        if (!canAccessCamera || !canRecordAudio || !canReadFiles || !canWriteFiles) {
            // Missing permissions ; request them
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                PERMISSIONS_REQUEST
            )
        }
    }

    companion object {
        private const val TAG = "ProfileActivity"
        private const val PERMISSIONS_REQUEST = 1220
    }

    private fun stringToLong(username : String) : Long {
        var id = ""
        var i = 0
        loop@ for (x in username) {
            if ( x.equals('@', true) || i>10){
                break@loop
            } else {
                id += charToLong(x)
            }
        }
        return id.toLong()
    }

    private fun charToLong(letter : Char) : String {
        if (letter.equals('.',true) || letter.equals('-',true) || letter.equals('0',true) || letter.equals('1',true) || letter.equals('.',true) || letter.equals('.',true) || letter.equals('.',true) || letter.equals('.',true) || letter.equals('.',true) || letter.equals('.',true) || letter.equals('.',true) || letter.equals('.',true)){
           return((letter.toByte()-44).toString())
        } else{
            return((letter.toByte()-94).toString())
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        ua?.unregister()
        connectedSession = null
    }
}