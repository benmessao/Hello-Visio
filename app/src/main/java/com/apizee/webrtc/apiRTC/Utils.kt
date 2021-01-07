package com.apizee.webrtc.apiRTC

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.util.*

/**
 * Utility functions
 *
 */
internal class Utils {
    companion object {
        private const val TAG = "Utils"

        fun getMimeType(context: Context, uri: Uri): String {
            var mimeType: String?
            mimeType = context.contentResolver.getType(uri)
            if (mimeType == null || mimeType == "*/*") {
                val fileExtension = MimeTypeMap.getFileExtensionFromUrl(getFileName(context, uri))
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase(Locale.ROOT))
            }
            if (mimeType == null)
                mimeType = "application/octet-stream"

            return mimeType
        }

        /**
         * Obtains the file name for a URI using content resolvers. Taken from the following link
         * https://developer.android.com/training/secure-file-sharing/retrieve-info.html#RetrieveFileInfo
         *
         * @param uri a uri to query
         * @return the file name with no path
         */
        fun getFileName(context: Context, uri: Uri): String? {
            // Obtain a cursor with information regarding this uri
            val cursor = context.contentResolver.query(uri, null, null, null, null)

            if (cursor == null)
                return null

            if (cursor.count <= 0) {
                cursor.close()
                return null
            }
            cursor.moveToFirst()
            val fileName = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            cursor.close()
            return fileName
        }
    }
}