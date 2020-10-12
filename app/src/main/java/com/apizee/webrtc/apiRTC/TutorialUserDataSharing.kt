package com.apizee.webrtc.apiRTC

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.apizee.apiRTC.Session
import com.apizee.apiRTC.UserAgent
import es.dmoral.toasty.Toasty
import kotlinx.android.synthetic.main.tutorial_user_data_sharing.*
import org.json.JSONException
import org.json.JSONObject


class TutorialUserDataSharing : AppCompatActivity() {
    private var ua: UserAgent? = null
    private var cloudUrl = "https://cloud.apizee.com"
    private var connectedSession: Session? = null
    private var listContactsString = arrayListOf<String>()
    private var listContactsAdapter: ArrayAdapter<*>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tutorial_user_data_sharing)

        listContactsAdapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, listContactsString)
        listContacts.adapter = listContactsAdapter

        // Close view when back button pressed
        buttonBack.setOnClickListener {
            finish()
        }

        buttonSetUserData.setOnClickListener {
            val userData = inputUserData.text.toString()
            if (userData != "") {
                try {
                    val jsonUserData = JSONObject(userData)
                    connectedSession?.setUserData(jsonUserData)
                } catch (e: JSONException) {
                    toast(ToastyType.TOASTY_ERROR, "Invalid JSON user data")
                }
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
            // Display user data of each member of the group
            connectedSession?.getContacts()?.forEach { contact -> listContactsString.add("usertata(${contact.getId()})=\n${contact.getUserData()}") }
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
        private const val TAG = "TutorialUserDataSharing"
    }
}