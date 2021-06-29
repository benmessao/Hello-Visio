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

class PeerToPeerCall : AppCompatActivity(){
    private var ua: UserAgent? = null
    private var cloudUrl = "https://cloud.apizee.com"
    private var connectedSession: Session? = null
    private var activeConversation: Conversation? = null
    private var activePushDataId: String? = null
    private var listContactsString = arrayListOf<String>()
    private var listContactsAdapter: ArrayAdapter<*>? = null


}