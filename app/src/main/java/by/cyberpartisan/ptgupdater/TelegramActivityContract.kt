package by.cyberpartisan.ptgupdater

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import java.lang.Exception

class TelegramActivityContract(var uri: Uri?, private val zipPassword: ByteArray?) : ActivityResultContract<ActivityInfo, Boolean>() {
    override fun createIntent(context: Context, input: ActivityInfo): Intent {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.setClassName(input.applicationInfo.packageName, input.name)
        intent.setDataAndType(uri, "application/zip")
        intent.putExtra("zipPassword", zipPassword)
        intent.putExtra("fromUpdater", true)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean = when {
        resultCode != Activity.RESULT_OK -> false
        else -> true
    }
}