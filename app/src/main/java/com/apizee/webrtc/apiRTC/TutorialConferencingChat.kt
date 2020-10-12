package com.apizee.webrtc.apiRTC

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import com.apizee.apiRTC.Contact
import com.apizee.apiRTC.Conversation
import com.apizee.apiRTC.Conversation.Companion.EVENT_CONTACT_JOINED
import com.apizee.apiRTC.Conversation.Companion.EVENT_CONTACT_LEFT
import com.apizee.apiRTC.Session
import com.apizee.apiRTC.UserAgent
import es.dmoral.toasty.Toasty
import kotlinx.android.synthetic.main.tutorial_conferencing_chat.*

class TutorialConferencingChat : AppCompatActivity() {
    private var ua: UserAgent? = null
    private var cloudUrl = "https://cloud.apizee.com"
    private var connectedSession: Session? = null
    private var activeConversation: Conversation? = null

    private var listContactsString = arrayListOf<String>()
    private var listContactsAdapter: ArrayAdapter<*>? = null

    private fun showChatBox() {
        runOnUiThread {
            conversationSelector.visibility = View.GONE
            layoutChat.visibility = View.VISIBLE
        }
    }

    //Wrapper to send a message to everyone in the conversation and display sent message in UI
    private fun sendMessageToActiveConversation(message: String) {
        val conversation = activeConversation
        if (message != "" && conversation != null) {
            addTextChatMessage("Me", message)

            //Actually send message to active contact
            conversation.sendMessage(message)
        }
    }

    private fun joinConversation(name: String) {
        val session = connectedSession

        if (name == "" || session == null)
            return

        activeConversation = session.getOrCreateConversation(name)

        //Listen to incoming messages from conversation
        activeConversation?.on(Conversation.EVENT_MESSAGE) {
            val message = it[0] as Conversation.Message
            addTextChatMessage(message.sender.getId(), message.content)
        }

        //Listen for any participants entering or leaving the conversation
        activeConversation?.on(EVENT_CONTACT_JOINED) {
            val contact = it[0] as Contact
            Log.d(TAG, "Contact that has joined : ${contact.getId()}")
            renderUserList()
        }

        activeConversation?.on(EVENT_CONTACT_LEFT) {
            val contact = it[0] as Contact
            Log.d(TAG, "Contact that has left : ${contact.getId()}")
            renderUserList()
        }

        activeConversation?.join { joinStatus, event ->
            when (joinStatus) {
                Conversation.Result.OK -> {
                    //Conversation was successfully joined
                    runOnUiThread {
                        textRoom.text = "Room : ${activeConversation?.getName()}"
                    }
                    showChatBox()
                    renderUserList()
                }
                Conversation.Result.FAILED -> {
                    toast(ToastyType.TOASTY_ERROR, "Conversation join error")
                }
            }
        }

        // Ask focus on text input
        runOnUiThread { inputChat.requestFocus() }
    }

    private fun renderUserList() {
        listContactsString.clear()
        activeConversation?.getContacts()?.forEach { contact -> listContactsString.add(contact.getId()) }

        runOnUiThread {
            listContactsAdapter?.notifyDataSetChanged()
        }
    }

    private fun addTextChatMessage(senderId: String, message: String) {
        val messageHtml = "<b>$senderId</b> : $message<br/>"
        //Display message in UI
        runOnUiThread {
            textChat.append(messageHtml.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY) })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tutorial_conferencing_chat)

        listContactsAdapter = ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, listContactsString)

        listContacts.adapter = listContactsAdapter
        // Allow scrolling
        textChat.movementMethod = ScrollingMovementMethod()

        // Close view when back button pressed
        buttonBack.setOnClickListener {
            finish()
        }

        //==============================
        // START CHAT
        //==============================
        buttonConversationSet.setOnClickListener {
            // Get contact from it's id
            val conversationName = inputConversation.text.toString()
            joinConversation(conversationName)
        }
        // Handle enter key
        inputConversation.setOnKeyListener(View.OnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                val conversationName = inputConversation.text.toString()
                joinConversation(conversationName)
                return@OnKeyListener true
            }
            false
        })

        //==============================
        // SEND CHAT MESSAGE TO ACTIVE CONTACT
        //==============================
        sendChat.setOnClickListener {
            sendMessageToActiveConversation(inputChat.text.toString())
            inputChat.text.clear()
        }
        inputChat.setOnKeyListener(View.OnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                sendMessageToActiveConversation(inputChat.text.toString())
                inputChat.text.clear()
                return@OnKeyListener true
            }
            false
        })

        start()
    }

    private fun start() {
        //==============================
        // CREATE USER AGENT
        //==============================
        val optionsUa = UserAgent.UserAgentOptions(uri = "apzkey:myDemoApiKey")
        ua = UserAgent(this, optionsUa)

        //==============================
        // REGISTER
        //==============================
        val optionsRegister = UserAgent.RegisterInformation(cloudUrl = cloudUrl)
        ua?.register(optionsRegister) { result, session ->
            when (result) {
                UserAgent.Result.OK -> {
                    Log.d(TAG, "Session successfully connected")
                    // Save session
                    connectedSession = session ?: return@register

                    runOnUiThread {
                        // Display user number
                        textRoom.text = "ID : ${session.getId()}"
                    }

                }
                UserAgent.Result.FAILED -> {
                    toast(ToastyType.TOASTY_ERROR, "User agent registering failed")
                    finish()
                }
            }
        }
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
        super.onDestroy()
        ua?.unregister()
        connectedSession = null
    }

    companion object {
        private const val TAG = "TutorialConfChat"
    }
}