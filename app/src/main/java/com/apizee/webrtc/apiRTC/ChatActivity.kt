package com.apizee.webrtc.apiRTC

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.apizee.apiRTC.Contact
import kotlinx.android.synthetic.main.activity_chat.*

class ChatActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        chatActivity = this

        // Allow scrolling
        textChat.movementMethod = ScrollingMovementMethod()
        textContacts.movementMethod = ScrollingMovementMethod()

        // Restore saved text history
        textChat.text = fullText
        textContacts.text = contactsText

        // Close view when back button pressed
        buttonBack.setOnClickListener {
            finish()
        }

        sendChat.setOnClickListener {
            sendMessage()
        }

        // Handle enter key
        inputChat.setOnKeyListener(View.OnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                sendMessage()

                return@OnKeyListener true
            }
            false
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        chatActivity = null
    }

    private fun sendMessage() {
        // Send message
        VideoCallActivity.sendMessage("Me", inputChat.text.toString())
        // Clear input
        inputChat.text.clear()
    }

    companion object {
        private var chatActivity: ChatActivity? = null
        private var fullText: String = ""
        private var contactsText: String = ""

        /**
         * Add text to chat view
         *
         * @param source Message source
         * @param message Message content
         */
        fun addMessage(source: String, message: String) {
            val text = "$source : $message\n"
            // Save full text
            fullText += text

            chatActivity?.runOnUiThread {
                // Append text to chat view
                chatActivity?.textChat?.append(text)
            }
        }

        fun setContacts(contacts: ArrayList<Contact>?) {
            val text = StringBuilder("Active users:\n")
            contacts?.forEach { contact: Contact -> text.append("* ${contact.getId()}\n") }
            contactsText = text.toString()

            chatActivity?.runOnUiThread {
                // Append text to contact view
                chatActivity?.textContacts?.text = contactsText
            }
        }
    }
}