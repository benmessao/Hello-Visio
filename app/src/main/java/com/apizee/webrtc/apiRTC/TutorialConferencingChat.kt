package com.apizee.webrtc.apiRTC

import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
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
import com.apizee.apiRTC.Conversation.Companion.EVENT_NEW_MEDIA_AVAILABLE
import com.apizee.apiRTC.Conversation.Companion.EVENT_TRANSFER_BEGUN
import com.apizee.apiRTC.Conversation.Companion.EVENT_TRANSFER_ENDED
import com.apizee.apiRTC.Conversation.Companion.EVENT_TRANSFER_PROGRESS
import com.apizee.apiRTC.Session
import com.apizee.apiRTC.UserAgent
import com.apizee.webrtc.apiRTC.Utils.Companion.getFileName
import com.apizee.webrtc.apiRTC.Utils.Companion.getMimeType
import es.dmoral.toasty.Toasty
import kotlinx.android.synthetic.main.tutorial_conferencing_chat.*

class TutorialConferencingChat : AppCompatActivity() {
    private var ua: UserAgent? = null
    private var cloudUrl = "https://cloud.apizee.com"
    private var connectedSession: Session? = null
    private var activeConversation: Conversation? = null
    private var activePushDataId: String? = null
    private var listContactsString = arrayListOf<String>()
    private var listContactsAdapter: ArrayAdapter<*>? = null

    private fun showChatBox() {
        runOnUiThread {
            conversationSelector.visibility = View.GONE
            layoutChat.visibility = View.VISIBLE
        }
    }

    private fun setUploadProgress(percentage: Number) {
        runOnUiThread {
            progressBarUpload.progress = percentage.toInt()
        }
    }

    private fun showUpload(show: Boolean) {
        runOnUiThread {
            if (show) {
                layoutUploadFile.visibility = View.VISIBLE
                buttonSendFile.disable()
                buttonCancelPush.enable()
            } else {
                layoutUploadFile.visibility = View.GONE
                buttonCancelPush.disable()
                buttonSendFile.enable()
            }
        }
    }

    //Wrapper to send a message to everyone in the conversation and display sent message in UI
    private fun sendMessageToActiveConversation(message: String) {
        val conversation = activeConversation
        if (message != "" && conversation != null) {
            addTextChatMessage("Me", message)

            //Actually send message to active contact
            conversation.sendMessage(message).then {
                //Message successfully sent!
            }.catch {
                val error = it as String
                //An error occured...
                val messageLine = "<li><i>Could not send message to conversation '${conversation.getName()}' (reason '$error'): '$message'</i></li><br/>"
                addTextChatMessage("System", messageLine, true)
            }
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

        activeConversation?.join()
                ?.then {
                    //Conversation was successfully joined
                    runOnUiThread {
                        textRoom.text = "Room : ${activeConversation?.getName()}"
                    }
                    showChatBox()
                    renderUserList()
                }?.catch {
                    val error = it as String
                    toast(ToastyType.TOASTY_ERROR, "Conversation join error : '$error'")
                }


        activeConversation?.on(EVENT_NEW_MEDIA_AVAILABLE)
        {
            val sender = it[0] as Contact
            val cloudMediaInfo = it[1] as Session.CloudMediaInfo
            addTextChatMessage(sender.getId(), "<a href=\"${Html.escapeHtml(cloudMediaInfo.url)}\">Received file ${Html.escapeHtml(cloudMediaInfo.id)}</a>", true)
            toast(ToastyType.TOASTY_INFO, "New media available from ${sender.getId()} : ${cloudMediaInfo.url}")
        }

        activeConversation?.on(EVENT_TRANSFER_BEGUN)
        {
            val event = it[0] as Conversation.Companion.EventTransferBegun
            activePushDataId = event.id
            addTextChatMessage("System", "Transfer of '${event.name}' with id '${event.id}' begun", false)
            showUpload(true)
        }

        activeConversation?.on(EVENT_TRANSFER_ENDED)
        {
            val event = it[0] as Conversation.Companion.EventTransferEnded
            activePushDataId = null
            addTextChatMessage("System", "Transfer of '${event.name}' with id '${event.id}' ended", false)
            showUpload(false)
        }

        activeConversation?.on(EVENT_TRANSFER_PROGRESS)
        {
            val event = it[0] as Conversation.Companion.EventTransferProgress
            setUploadProgress(event.percentage)
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

    private fun addTextChatMessage(senderId: String, message: String, useHtml: Boolean = false) {
        val senderHtml = "<b>${Html.escapeHtml(senderId)}</b> : "
        val messageHtml = "$message<br/>"
        val messageText = "$message\n"
        //Display message in UI
        runOnUiThread {
            textChat.append(HtmlCompat.fromHtml(senderHtml, HtmlCompat.FROM_HTML_MODE_LEGACY))
            if (useHtml)
                textChat.append(HtmlCompat.fromHtml(messageHtml, HtmlCompat.FROM_HTML_MODE_LEGACY))
            else
                textChat.append(messageText)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tutorial_conferencing_chat)

        listContactsAdapter = ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, listContactsString)

        listContacts.adapter = listContactsAdapter
        textChat.movementMethod = LinkMovementMethod.getInstance()

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

        buttonSendFile.setOnClickListener {
            showFileLoadChooser()
        }

        buttonCancelPush.setOnClickListener {
            val pushDataId = activePushDataId
            showUpload(false)
            if (pushDataId != null) {
                activeConversation?.cancelPushData(pushDataId)
                addTextChatMessage("System", "Transfer of file with id '$pushDataId' has been aborted. You can resume it by doing it again.", false)
                activePushDataId = null
            }
        }

        showUpload(false)

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

            runOnUiThread {
                textRoom.text = "ID : ${session.getId()}"
            }
        }?.catch {
            val error = it as String
            toast(ToastyType.TOASTY_ERROR, "User agent registration failed with '$error'")
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == FILE_CHOOSER_LOAD_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val uri = data.data ?: return

            val filename = getFileName(this, uri) ?: "unknown"
            val filetype = getMimeType(this, uri)

            val fileContent = contentResolver.openInputStream(uri)?.readBytes()
            if (fileContent == null) return

            toast(ToastyType.TOASTY_INFO, "Sending file '$filename'")
            activeConversation?.pushData(Session.PushDataBufferDescriptor(fileContent, filename, filetype, ttl = 30, overwrite = false))
                    ?.then {
                        val cloudMediaInfo = it as Session.CloudMediaInfo
                        addTextChatMessage("System", "<a href=\"${Html.escapeHtml(cloudMediaInfo.url)}\">Sent file ${Html.escapeHtml(cloudMediaInfo.id)}</a>", true)
                        sendMessageToActiveConversation("New file uploaded: <a href=\"${cloudMediaInfo.url}\" target=\"_blank\" <b>OPEN FILE</b></a>")
                        toast(ToastyType.TOASTY_SUCCESS, "File upload succeeded")
                    }?.catch { error ->
                        activePushDataId = null
                        showUpload(false)
                        toast(ToastyType.TOASTY_ERROR, "File upload failed: $error")
                    }
        }
    }

    private fun showFileLoadChooser() {
        val intent = Intent()
                .setType("*/*")
                .setAction(Intent.ACTION_GET_CONTENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(Intent.createChooser(intent, "Select a file to load"), FILE_CHOOSER_LOAD_REQUEST_CODE)
    }

    private fun View.disable() {
        background.setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_ATOP)
        isClickable = false
    }

    private fun View.enable() {
        background.colorFilter = null
        isClickable = true
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
        private const val TAG = "TutorialConfChat"
        private const val FILE_CHOOSER_LOAD_REQUEST_CODE = 1230
    }
}