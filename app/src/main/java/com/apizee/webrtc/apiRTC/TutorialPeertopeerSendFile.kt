package com.apizee.webrtc.apiRTC

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.apizee.apiRTC.*
import com.apizee.webrtc.apiRTC.Utils.Companion.getFileName
import com.apizee.webrtc.apiRTC.Utils.Companion.getMimeType
import es.dmoral.toasty.Toasty
import kotlinx.android.synthetic.main.tutorial_peertopeer_send_file.*


class TutorialPeertopeerSendFile : AppCompatActivity() {
    private var ua: UserAgent? = null
    private var cloudUrl = "https://cloud.apizee.com"
    private var connectedSession: Session? = null
    private var listContactsString = arrayListOf<String>()
    private var listContactsAdapter: ArrayAdapter<*>? = null
    private var lastFileContent: ByteArray? = null
    private var lastInvitation: SentInvitation? = null
    private var selectedContactId: String? = null
    private var uploadUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tutorial_peertopeer_send_file)

        listContactsAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_activated_1, listContactsString)
        listContacts.adapter = listContactsAdapter
        listContacts.choiceMode = ListView.CHOICE_MODE_SINGLE
        listContacts.setOnItemClickListener { parent, view, position, id ->
            selectedContactId = listContactsAdapter?.getItem(position).toString()
            renderUploadFile()
        }

        // Close view when back button pressed
        buttonBack.setOnClickListener {
            finish()
        }

        buttonBrowseFile.setOnClickListener {
            showFileLoadChooser()
        }

        buttonSaveFile.setOnClickListener {
            showFileSaveChooser()
        }

        buttonSendFile.setOnClickListener {
            val contactId = selectedContactId
            val uri = uploadUri
            if (contactId == null || uri == null) return@setOnClickListener
            val contact = connectedSession?.getOrCreateContact(contactId)

            val filename = getFileName(this, uri) ?: "unknown"
            val mimeType = getMimeType(this, uri)

            val data = contentResolver.openInputStream(uri)?.readBytes()
            if (data == null) return@setOnClickListener
            val invitation = contact?.sendFile(Session.FileInfo(filename, mimeType), data)
            lastInvitation = invitation
            if (invitation != null) {
                runOnUiThread {
                    setUploadProgress(0)
                    textStatusSend.text = "Waiting accept"
                    buttonSendFile.disable()
                    buttonCancelSend.enable()
                }
            }

            contact?.on(Contact.EVENT_FILE_TRANSFER_PROGRESS) {
                val fileInfo = it[0] as Session.FileInfo
                val transferInformation = it[1] as Session.TransferInformation
                setUploadProgress(transferInformation.percentage)
            }

            contact?.on(Contact.EVENT_FILE_SENT_SUCCESSFULLY) {
                val fileInfo = it[0] as Session.FileInfo
                val transferInformation = it[1] as Session.TransferInformation
                toast(ToastyType.TOASTY_SUCCESS, "File '${fileInfo.name}' successfully sent")
                runOnUiThread {
                    textStatusSend.text = "OK"
                    buttonSendFile.enable()
                    buttonCancelSend.disable()
                }
                lastInvitation = null
            }

            contact?.on(Contact.EVENT_FILE_TRANSFER_ERROR) {
                val error = it[0] as String
                val invitationId = it[1] as String
                toast(ToastyType.TOASTY_ERROR, "Transfer failed for invitation '${invitationId}' : $error")
                runOnUiThread {
                    textStatusSend.text = "FAILED"
                    buttonSendFile.enable()
                    buttonCancelSend.disable()
                }
                lastInvitation = null
            }
        }

        buttonCancelSend.setOnClickListener {
            lastInvitation?.cancel()
            runOnUiThread {
                textStatusSend.text = "Cancelled"
                buttonSendFile.enable()
                buttonCancelSend.disable()
            }
        }

        showInvitation(false)
        showDownload(false)
        showPreview(false)
        runOnUiThread {
            buttonSendFile.disable()
            buttonCancelSend.disable()
        }
        start()
    }

    private fun setDownloadProgress(percentage: Number) {
        runOnUiThread {
            progressBarDownload.progress = percentage.toInt()
        }
    }

    private fun showDownload(show: Boolean) {
        runOnUiThread {
            if (show)
                layoutDownloadFile.visibility = View.VISIBLE
            else
                layoutDownloadFile.visibility = View.GONE
        }
    }

    private fun setUploadProgress(percentage: Number) {
        runOnUiThread {
            progressBarUpload.progress = percentage.toInt()
        }
    }

    private fun showInvitation(show: Boolean) {
        runOnUiThread {
            if (show)
                layoutAnswerFile.visibility = View.VISIBLE
            else {
                layoutAnswerFile.visibility = View.GONE
                buttonAcceptFile.setOnClickListener(null)
                buttonRejectFile.setOnClickListener(null)
            }
        }
    }

    private fun showSaveButton(show: Boolean) {
        runOnUiThread {
            if (show)
                buttonSaveFile.visibility = View.VISIBLE
            else
                buttonSaveFile.visibility = View.GONE
        }
    }

    private fun showPreview(show: Boolean) {
        runOnUiThread {
            if (show)
                imageView.visibility = View.VISIBLE
            else
                imageView.visibility = View.GONE
        }
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

        ua?.register(optionsRegister)?.then { itSession ->
            val session = itSession as Session
            Log.d(TAG, "Session successfully connected")
            connectedSession = session
            connectedSession?.setUsername("guest")

            //Listen to contact list updates
            connectedSession?.on(Session.EVENT_CONTACT_LIST_UPDATE)
            {
                renderUserList()
                renderUploadFile()
            }
            connectedSession?.on(Session.EVENT_FILE_TRANSFER_INVITATION)
            { it1 ->
                val receivedFileTransferInvitation = it1[0] as ReceivedFileTransferInvitation
                Log.d(TAG, "invitation : $receivedFileTransferInvitation")

                buttonAcceptFile.setOnClickListener {
                    setDownloadProgress(0)
                    lastFileContent = null
                    showInvitation(false)
                    showDownload(true)
                    showSaveButton(false)
                    showPreview(false)
                    receivedFileTransferInvitation.accept().then {
                        val fileObject = it as ReceivedFileTransferInvitation.FileObject
                        toast(ToastyType.TOASTY_SUCCESS, "File '${fileObject.name}' successfully received")
                        val bmp = BitmapFactory.decodeByteArray(fileObject.file, 0, fileObject.file.size)
                        runOnUiThread {
                            if (bmp != null) {
                                imageView.setImageBitmap(bmp)
                                showPreview(true)
                            }
                            lastFileContent = fileObject.file
                            showSaveButton(true)
                        }
                    }.catch {
                        val error = it as String
                        toast(ToastyType.TOASTY_ERROR, "receivedFileTransferInvitation accept failure '$error'")
                        showDownload(false)
                    }


                }

                buttonRejectFile.setOnClickListener {
                    receivedFileTransferInvitation.decline("User rejected file transfer")
                    showInvitation(false)
                }

                receivedFileTransferInvitation.on(Invitation.EVENT_STATUS_CHANGE) {
                    val status = it[0] as Invitation.StatusChangeInfo
                    Log.d(TAG, "invitation : $receivedFileTransferInvitation")

                    if (status.status == Invitation.Status.INVITATION_STATUS_ENDED || status.status == Invitation.Status.INVITATION_STATUS_CANCELLED) {
                        showInvitation(false)
                    }
                }

                receivedFileTransferInvitation.on(ReceivedFileTransferInvitation.EVENT_PROGRESS) {
                    val status = it[0] as Session.TransferInformation
                    setDownloadProgress(status.percentage)
                }

                runOnUiThread {
                    textDownloadName.text = receivedFileTransferInvitation.getFileInfo().name
                    textDownloadName2.text = receivedFileTransferInvitation.getFileInfo().name
                }
                showInvitation(true)
            }
            runOnUiThread {
                textId.text = "ID : ${session.getId()}"
            }
        }?.catch {
            val error = it as String
            toast(ToastyType.TOASTY_ERROR, "User agent registration failed with '$error'")
            finish()
        }
    }

    private fun renderUserList() {
        runOnUiThread {
            var newPosition = -1
            var i = 0
            listContactsString.clear()
            // Display user data of each member of the group
            connectedSession?.getContacts()?.forEach {
                if (it.isOnline()) {
                    listContactsString.add("${it.getId()}")
                    if (it.getId() == selectedContactId) newPosition = i
                    i++
                }
            }
            listContactsAdapter?.notifyDataSetChanged()
            listContacts.setItemChecked(listContacts.checkedItemPosition, false)
            if (newPosition != -1) {
                listContacts.setItemChecked(newPosition, true)
            }
        }
    }

    private fun renderUploadFile() {
        var filename: String? = null
        val uri = uploadUri
        if (uri != null) filename = getFileName(this, uri)
        runOnUiThread {
            textUploadName.text = "$filename -> $selectedContactId"
        }
    }

    private fun openFile(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            val mimeType = getMimeType(this, uri)
            intent.setDataAndType(uri, mimeType)
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast(ToastyType.TOASTY_ERROR, "No application available to open this file")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == FILE_CHOOSER_SAVE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val selectedFile = data.data //The uri with the location of the file

            val content = lastFileContent
            if (selectedFile != null && content != null) {
                try {
                    val outputStream = contentResolver.openOutputStream(selectedFile)
                    outputStream?.write(content)
                    outputStream?.close()
                    outputStream?.let { toast(ToastyType.TOASTY_SUCCESS, "File saved.") }
                } catch (e: Exception) {
                    toast(ToastyType.TOASTY_ERROR, "Impossible to save the file for reason '$e'. Please retry.")
                }
            } else
                toast(ToastyType.TOASTY_ERROR, "No file to save")
        }

        if (requestCode == FILE_CHOOSER_LOAD_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val uri = data.data ?: return
            uploadUri = uri
            renderUploadFile()
            runOnUiThread { buttonSendFile.enable() }
        }
    }

    private fun View.disable() {
        background.setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_ATOP)
        isClickable = false
    }

    private fun View.enable() {
        background.colorFilter = null
        isClickable = true
    }

    private fun showFileLoadChooser() {
        val intent = Intent()
                .setType("*/*")
                .setAction(Intent.ACTION_GET_CONTENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(Intent.createChooser(intent, "Select a file to load"), FILE_CHOOSER_LOAD_REQUEST_CODE)
    }

    private fun showFileSaveChooser() {
        val intent = Intent()
                .setType("*/*")
                .setAction(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_TITLE, textDownloadName2.text)

        startActivityForResult(Intent.createChooser(intent, "Select a filename to save"), FILE_CHOOSER_SAVE_REQUEST_CODE)
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
        private const val TAG = "TutorialP2PSendFile"

        private const val FILE_CHOOSER_LOAD_REQUEST_CODE = 1230
        private const val FILE_CHOOSER_SAVE_REQUEST_CODE = 1231
    }
}