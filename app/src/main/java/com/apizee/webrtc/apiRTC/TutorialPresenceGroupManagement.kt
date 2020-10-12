package com.apizee.webrtc.apiRTC

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.apizee.apiRTC.Session
import com.apizee.apiRTC.UserAgent
import es.dmoral.toasty.Toasty
import kotlinx.android.synthetic.main.tutorial_presence_group_management.*
import org.json.JSONObject


class TutorialPresenceGroupManagement : AppCompatActivity() {
    private var ua: UserAgent? = null
    private var cloudUrl = "https://cloud.apizee.com"
    private var connectedSession: Session? = null
    private var listContactsString = arrayListOf<String>()
    private var listContactsAdapter: ArrayAdapter<*>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tutorial_presence_group_management)

        listContactsAdapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, listContactsString)
        listContacts.adapter = listContactsAdapter

        // Close view when back button pressed
        buttonBack.setOnClickListener {
            finish()
        }

        buttonJoin.setOnClickListener {
            val group = inputGroup.text.toString()
            if (group != "") {
                connectedSession?.joinGroup(group)
            }
        }
        buttonLeave.setOnClickListener {
            val group = inputGroup.text.toString()
            if (group != "") {
                connectedSession?.leaveGroup(group)
            }
        }
        buttonSubscribe.setOnClickListener {
            val group = inputGroup.text.toString()
            if (group != "") {
                connectedSession?.subscribeToGroup(group)
            }
        }
        buttonUnsubscribe.setOnClickListener {
            val group = inputGroup.text.toString()
            if (group != "") {
                connectedSession?.unsubscribeToGroup(group)
            }
        }

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
        /*
// Different cases to test joining groups on register
// Case 0: no settings, apiRTC client join and subscribes to group "default"

// Case 1 : join and subscribe to group "testGroup"
        optionsRegister.groups = arrayListOf("testGroup")
        optionsRegister.subscribeTo = arrayListOf("testGroup")

// Case 2 : join group "test_pub" and subscribe to group "test_sub"
        optionsRegister.groups = arrayListOf("test_pub")
        optionsRegister.subscribeTo = arrayListOf("test_sub")

// Case 3 : Disable group presence on join and subscribe
        optionsRegister.groups = arrayListOf("deactivated")
        optionsRegister.subscribeTo = arrayListOf("deactivated")

// Case 4: Join and subscribe to multiple groups
        optionsRegister.groups = arrayListOf("default", "testGroup")
        optionsRegister.subscribeTo = arrayListOf("default", "testGroup")
*/

        ua?.register(optionsRegister) { result, session ->
            when (result) {
                UserAgent.Result.OK -> {
                    Log.d(TAG, "Session successfully connected")
                    connectedSession = session ?: return@register
                    connectedSession?.setUsername("guest")

                    runOnUiThread {
                        // Display user number
                        textId.text = "ID : ${session.getId()}"
                    }

                    //Listen to contact list updates
                    connectedSession?.on(Session.EVENT_CONTACT_LIST_UPDATE)
                    {
                        renderUserList()
                    }
                }
                UserAgent.Result.FAILED -> {
                    toast(ToastyType.TOASTY_ERROR, "User agent registering failed")
                    finish()
                }
            }
        }
    }

    private fun renderUserList() {
        runOnUiThread {
            listContactsString.clear()
            connectedSession?.getContacts()?.forEach { contact -> listContactsString.add("${contact.getId()} ${contact.getGroups()}") }
            listContactsAdapter?.notifyDataSetChanged()
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
        private const val TAG = "TutorialPresenceGroup"
    }
}