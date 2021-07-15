 package com.apizee.webrtc.apiRTC

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import com.apizee.apiRTC.Contact
import com.apizee.apiRTC.Session
import com.apizee.apiRTC.UserAgent
import es.dmoral.toasty.Toasty
import kotlinx.android.synthetic.main.tutorial_peertopeer_chat.*

class TutorialPeertopeerChat : AppCompatActivity() {
    private var ua: UserAgent? = null
    private var cloudUrl = "https://cloud.apizee.com"
    private var connectedSession: Session? = null
    private var activeContact: Contact? = null
    private var activeChats: HashMap<String, String> = HashMap()

    private var listContactsString = arrayListOf<String>()
    private var listContactsAdapter: ArrayAdapter<*>? = null

    private fun showChatBox() {
        runOnUiThread {
            layoutChat.visibility = View.VISIBLE
        }
    }

    //Wrapper to send a message to a contact and display sent message in UI
    private fun sendMessageToActiveContact(message: String) {
        val contact = activeContact
        if (message != "" && contact != null) {
            addTextChatMessage(contact.getId(), "<b>Me</b> : $message<br/>")

            //Actually send message to active contact
            contact.sendMessage(message).then {
                //Message successfully sent!
            }.catch {
                val error = it as String
                //An error occured...
                val messageLine = "<li><i>Could not send message to contact '${contact.getId()}' (reason '$error'): '$message'</i></li><br/>"
                addTextChatMessage(contact.getId(), messageLine)
            }
        }
    }

    //Select active contact for chatbox UI
    private fun setActiveContact(contactId: String) {
        if (activeContact == null) {
            // Show chatbox
            showChatBox()
        }
        //==============================
        // GET CONTACT OBJECT
        //==============================
        val contact = connectedSession?.getOrCreateContact(contactId)
        activeContact = contact
        //Restore previous chat messsages
        if (contact != null) {
            runOnUiThread {
                textChat.text = activeChats[contact.getId()]?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY) }
            }
        }
        // Ask focus on text input
        runOnUiThread { inputChat.requestFocus() }
    }

    //Initialize chat history for contact if needed
    private fun addContactToActiveChats(contactId: String) {
        if (!activeChats.containsKey(contactId)) {
            activeChats[contactId] = ""
            listContactsString.add(contactId)
            runOnUiThread {
                listContactsAdapter?.notifyDataSetChanged()
                listContacts.setItemChecked(listContactsString.indexOf(contactId), true)
            }
        }
    }

    private fun addTextChatMessage(senderId: String, message: String) {
        activeChats[senderId] += message //save message
        //Display message in UI
        runOnUiThread {
            if (activeContact?.getId() == senderId) {
                textChat.text = ""
                textChat.append(activeChats[senderId]?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY) })
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tutorial_peertopeer_chat)

        listContactsAdapter = ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_activated_1, listContactsString)

        listContacts.choiceMode = ListView.CHOICE_MODE_SINGLE
        listContacts.adapter = listContactsAdapter
        listContacts.setOnItemClickListener { parent, view, position, id ->
            val contactId = listContactsAdapter?.getItem(position).toString()
            setActiveContact(contactId)
        }

        // Allow scrolling
        textChat.movementMethod = ScrollingMovementMethod()

        // Close view when back button pressed
        buttonBack.setOnClickListener {
            finish()
        }

        //==============================
        // START CHAT
        //==============================
        buttonContactAdd.setOnClickListener {
            // Get contact from it's id
            val contactId = inputContact.text.toString()
            if (contactId != "") {
                inputContact.text.clear()
                addContactToActiveChats(contactId)
                setActiveContact(contactId)
            }
        }
        // Handle enter key
        inputContact.setOnKeyListener(View.OnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                // Get contact from it's id
                val contactId = inputContact.text.toString()
                if (contactId != "") {
                    inputContact.text.clear()
                    addContactToActiveChats(contactId)
                    setActiveContact(contactId)
                }
                return@OnKeyListener true
            }
            false
        })

        //==============================
        // SEND CHAT MESSAGE TO ACTIVE CONTACT
        //==============================
        sendChat.setOnClickListener {
            sendMessageToActiveContact(inputChat.text.toString())
            inputChat.text.clear()
        }
        inputChat.setOnKeyListener(View.OnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                sendMessageToActiveContact(inputChat.text.toString())
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
                addContactToActiveChats(message.sender.getId())
                if (activeContact == null) {
                    setActiveContact(message.sender.getId())
                }
                val messageLine = "<b>${message.sender.getId()}</b> : ${message.content}<br/>"
                addTextChatMessage(message.sender.getId(), messageLine)
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

    override fun onDestroy() {
        super.onDestroy()
        ua?.unregister()
        connectedSession = null
    }

    companion object {
        private const val TAG = "TutorialPeertopeerChat"
    }
}