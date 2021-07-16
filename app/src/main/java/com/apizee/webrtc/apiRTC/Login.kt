package com.apizee.webrtc.apiRTC

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.login.*

class Login : AppCompatActivity(){

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.login)

        val bLogin = findViewById<Button>(R.id.loginButton)
        val username = findViewById<EditText>(R.id.userString)

        bLogin.setOnClickListener {
            //Get username
            val sUsername = username.text.toString()
            val id = stringToLong(sUsername)
            //Translate string_username to long_username
            userId.setText(id.toString())
        }
    }

    private fun stringToLong(username : String) : Long {
        var id = ""
        for (x in username) {
            id += charToLong(x)
        }
        return id.toLong()
    }

    private fun charToLong(letter : Char) : String {
        val bLetter = letter.toByte()-96
        return(bLetter.toString())
    }

}